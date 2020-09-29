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
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

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
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and finalizing move.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    WINNING_TRANSACTION,
                    SIGNING_TRANSACTION,
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
            progressTracker.currentStep = AcceptGameFlow.Initiator.Companion.GENERATING_TRANSACTION
            val gQueryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameStateId))
            val bQueryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameStateId))
            val inputGameStateAndRef = serviceHub.vaultService.queryBy<GameState>(gQueryCriteria).states.single()
            val inputBoardStateAndRef = serviceHub.vaultService.queryBy<BoardState>(bQueryCriteria).states.single()

            val inputGameState = inputGameStateAndRef.state.data
            val inputBoardState = inputBoardStateAndRef.state.data
            val notary = inputGameStateAndRef.state.notary

            val txnBuilder = TransactionBuilder(notary)

            if (inputGameState.status == GameStatus.ACCEPTED && inputBoardState.moveNumber ==1) {
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
            val nextRow = (inputBoardState.boardMap?.filter { it.value.column == column }?.maxBy { it.value.row }?.value?.row
                    ?: 0 ) + 1
            val newBoard = inputBoardState.boardMap?.plus(mapOf(Pair(inputBoardState.moveNumber, Cell(column, nextRow , player))))

            val gameProgress = GameValidator.verifyGameProgress(inputBoardState.boardMap!!.toMap(), inputBoardState.boardSize)

            val boardCommand = when(gameProgress.first) {
                GameStatus.ACTIVE -> {
                    BoardContract.Commands.Play()
                }
                GameStatus.COMPLETE -> {
                    BoardContract.Commands.WinningPlay()
                }
                GameStatus.DRAW -> {
                    BoardContract.Commands.Draw()
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
                    inputBoardState.status,
                    newBoard
            )
            val playBoardCommand = Command(boardCommand, player.owningKey)

            txnBuilder.addInputState(inputBoardStateAndRef).addOutputState(outputBoardState).addCommand(playBoardCommand)

            /******************************************************
            STEP2 - Verifying constraints of the game and board
             ******************************************************/
            progressTracker.currentStep = AcceptGameFlow.Initiator.Companion.VERIFYING_TRANSACTION
            txnBuilder.verify(serviceHub)

            /******************************************************
            STEP3 - Sign and finalize new game request
             ******************************************************/
            progressTracker.currentStep = AcceptGameFlow.Initiator.Companion.SIGNING_TRANSACTION

            //The initiator is the only party that needs to sign, we then will send the pending game to the participant.
            val fullySignedTx = serviceHub.signInitialTransaction(txnBuilder)

            progressTracker.currentStep = AcceptGameFlow.Initiator.Companion.FINALIZING_TRANSACTION
            val participantFlows = initiateFlow(inputGameState.initiator)

            return subFlow(FinalityFlow(fullySignedTx, listOf(participantFlows), AcceptGameFlow.Initiator.Companion.FINALIZING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(flowSession))
        }
    }
}