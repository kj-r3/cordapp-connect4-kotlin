package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.connect4.contracts.BoardContract
import com.connect4.contracts.GameContract
import com.connect4.states.BoardState
import com.connect4.states.Color
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

object AcceptGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator (private val gameStateId: UniqueIdentifier, _color: String) : FlowLogic<SignedTransaction>() {

        private val color = Color.valueOf(_color)

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating accept game based on input.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying game configuration.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing off on game.")
            object FINALIZING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and finalizing request.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALIZING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val participant = ourIdentity

            /*****************************************************
            STEP1 - Find GameState to accept and generate accept transaction
             ******************************************************/
            progressTracker.currentStep = GENERATING_TRANSACTION
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameStateId))
            val inputGameStateAndRef = serviceHub.vaultService.queryBy<GameState>(queryCriteria).states.single()

            val inputGameState = inputGameStateAndRef.state.data
            val notary = inputGameStateAndRef.state.notary
            val acceptGameCommand = Command(GameContract.Commands.AcceptGame(), participant.owningKey)
            val boardState = BoardState(UniqueIdentifier(),
                                        inputGameState.linearId,
                                        inputGameState.boardSize,
                                        inputGameState.initiator,
                                        inputGameState.participant,
                                        inputGameState.initiator)
            val newBoardCommand = Command(BoardContract.Commands.New(), participant.owningKey)

            val outputGameState = GameState(inputGameState.linearId,
                                            inputGameState.initiator,
                                            participant,
                                            inputGameState.initiatorColor,
                                            color,
                                            inputGameState.boardSize,
                                            GameStatus.ACCEPTED)

            val txnBuilder = TransactionBuilder(notary)
                    .addInputState(inputGameStateAndRef)
                    .addCommand(acceptGameCommand)
                    .addCommand(newBoardCommand)
                    .addOutputState(outputGameState, GameContract.ID)
                    .addOutputState(boardState, BoardContract.ID)

            /******************************************************
            STEP2 - Verifying constraints of the game and board
             ******************************************************/
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txnBuilder.verify(serviceHub)

            /******************************************************
            STEP3 - Sign and finalize new game request
             ******************************************************/
            progressTracker.currentStep = SIGNING_TRANSACTION

            //The initiator is the only party that needs to sign, we then will send the pending game to the participant.
            val fullySignedTx = serviceHub.signInitialTransaction(txnBuilder)

            progressTracker.currentStep = FINALIZING_TRANSACTION
            val participantFlows = initiateFlow(inputGameState.initiator)

            return subFlow(FinalityFlow(fullySignedTx, listOf(participantFlows), FINALIZING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Responder flow logic goes here.
            return subFlow(ReceiveFinalityFlow(flowSession))
        }
    }
}