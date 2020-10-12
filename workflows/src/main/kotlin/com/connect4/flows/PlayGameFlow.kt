package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.connect4.contracts.BoardContract
import com.connect4.contracts.GameContract
import com.connect4.states.BoardState
import com.connect4.states.Cell
import com.connect4.states.GameState
import com.connect4.states.GameStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object PlayGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val gameStateId: UniqueIdentifier, private val column: Int) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating play transaction.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying play.")
            object WINNING_TRANSACTION : ProgressTracker.Step("Winning play detected!")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing off on move.")
            object GATHERING_SIGNATURES : ProgressTracker.Step("Obtaining notary signature and finalizing move.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and finalizing move.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    WINNING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGNATURES,
                    FINALIZING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val player = ourIdentity

            /*****************************************************
            STEP1 - Find GameState to accept and generate accept transaction
             ******************************************************/
            progressTracker.currentStep = GENERATING_TRANSACTION
            val gQueryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameStateId))
            val inputGameStateAndRef = serviceHub.vaultService.queryBy<GameState>(gQueryCriteria).states.single()
            val inputBoardStateAndRef = serviceHub.vaultService.queryBy<BoardState>().states.asSequence().filter{it.state.data.gameState == gameStateId}.single()

            val inputGameState = inputGameStateAndRef.state.data
            val inputBoardState = inputBoardStateAndRef.state.data
            val notary = inputGameStateAndRef.state.notary

            val txnBuilder = TransactionBuilder(notary)

            val nextRow = (inputBoardState.boardMap?.filter { it.value.column == column }?.maxBy { it.value.row }?.value?.row ?: 0 ) + 1
            val newBoard = inputBoardState.boardMap?.plus(mapOf(Pair(inputBoardState.moveNumber, Cell(column, nextRow , player))))

            val gameProgress = GameValidator.verifyGameProgress(newBoard!!.toMap(), inputBoardState.boardSize)

            val boardCommand = when(gameProgress.first) {
                GameStatus.ACTIVE -> {
                    Command(BoardContract.Commands.Play(), player.owningKey)
                }
                GameStatus.COMPLETE -> {
                    Command(BoardContract.Commands.WinningPlay(), listOf(inputBoardState.initiator.owningKey, inputBoardState.participant.owningKey))
                }
                GameStatus.DRAW -> {
                    Command(BoardContract.Commands.Draw(), listOf(inputBoardState.initiator.owningKey, inputBoardState.participant.owningKey))
                }
                else -> throw FlowException("No valid status after move")
            }

            val outputBoardState = BoardState(inputBoardState.linearId,
                inputBoardState.gameState,
                inputBoardState.boardSize,
                inputBoardState.initiator,
                inputBoardState.participant,
                if(player == inputBoardState.participant) inputBoardState.initiator else inputBoardState.participant,
                inputBoardState.moveNumber+1,
                if(gameProgress.first == GameStatus.ACTIVE) GameStatus.ACTIVE else GameStatus.COMPLETE,
                newBoard
            )

            txnBuilder.addInputState(inputBoardStateAndRef).addOutputState(outputBoardState).addCommand(boardCommand)

            if (inputGameState.status == GameStatus.ACCEPTED && inputBoardState.moveNumber == 1) {
                val activateGameCommand = Command(GameContract.Commands.Activate(), player.owningKey)
                val outputGameState = GameState(inputGameState.linearId,
                        inputGameState.initiator,
                        inputGameState.participant,
                        inputGameState.initiatorColor,
                        inputGameState.participantColor,
                        inputGameState.boardSize,
                        GameStatus.ACTIVE)

                txnBuilder.addInputState(inputGameStateAndRef).addCommand(activateGameCommand).addOutputState(outputGameState)
            }

            if (gameProgress.first != GameStatus.ACTIVE) {
                progressTracker.currentStep = WINNING_TRANSACTION
                val completeGameCommand = Command(GameContract.Commands.CompleteGame(), inputGameState.participants.map{it.owningKey}.distinct())
                val outputGameState = GameState(inputGameState.linearId,
                        inputGameState.initiator,
                        inputGameState.participant,
                        inputGameState.initiatorColor,
                        inputGameState.participantColor,
                        inputGameState.boardSize,
                        gameProgress.first,
                        gameProgress.second)

                txnBuilder.addInputState(inputGameStateAndRef).addCommand(completeGameCommand).addOutputState(outputGameState)
            }

            /******************************************************
            STEP2 - Verifying constraints of the game and board
             ******************************************************/
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txnBuilder.verify(serviceHub)

            /******************************************************
            STEP3 - Sign and finalize new game request
             ******************************************************/
            progressTracker.currentStep = SIGNING_TRANSACTION

            //The player is the only party that needs to sign, we then will send the pending game to the other player.
            val partiallySignedTransaction = serviceHub.signInitialTransaction(txnBuilder)

            progressTracker.currentStep = FINALIZING_TRANSACTION

            val counterparty = if(player == inputBoardState.participant) inputBoardState.initiator else inputBoardState.participant
            val participantFlows = initiateFlow(counterparty)

            //send over game progress to responder to so responder can sign transaction if the game is no longer active
            participantFlows.send(gameProgress)

            val fullySignedTx = if(gameProgress.first != GameStatus.ACTIVE)
                subFlow(CollectSignaturesFlow(partiallySignedTransaction, listOf(participantFlows), GATHERING_SIGNATURES.childProgressTracker()))
                else partiallySignedTransaction

            val notarisedTxn = subFlow(FinalityFlow(fullySignedTx, listOf(participantFlows), FINALIZING_TRANSACTION.childProgressTracker()))

            serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(notarisedTxn))

            return notarisedTxn
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object RECEIVING_GAME_PROGRESS : ProgressTracker.Step("Receiving game progress.")
            object GATHERING_SIGNATURES : ProgressTracker.Step("Counter-signing completed game.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and finalizing move.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    RECEIVING_GAME_PROGRESS,
                    GATHERING_SIGNATURES,
                    FINALIZING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            progressTracker.currentStep = RECEIVING_GAME_PROGRESS
            val gameProgress = flowSession.receive<Pair<GameStatus, Party?>>().unwrap { it }

            progressTracker.currentStep = GATHERING_SIGNATURES
            val txId = if(gameProgress.first != GameStatus.ACTIVE) {
                val signTransactionFlow = object : SignTransactionFlow(flowSession){
                    override fun checkTransaction(stx: SignedTransaction) {
                        //Nothing to verify (yet)
                    }
                }
                subFlow(signTransactionFlow).id
            } else null

            progressTracker.currentStep = FINALIZING_TRANSACTION
            return subFlow(ReceiveFinalityFlow(flowSession, txId))
        }
    }
}