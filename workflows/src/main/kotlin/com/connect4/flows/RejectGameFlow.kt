package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.connect4.contracts.GameContract
import com.connect4.states.GameState
import com.connect4.states.GameStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object RejectGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator (private val gameStateId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {


        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating reject game based on input.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying rejection configuration.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing off on rejection.")
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
            val rejectGameCommand = Command(GameContract.Commands.RejectGame(), participant.owningKey)

            val outputGameState = GameState(inputGameState.linearId,
                    inputGameState.initiator,
                    participant,
                    inputGameState.initiatorColor,
                    null,
                    inputGameState.boardSize,
                    GameStatus.REJECTED)

            val txnBuilder = TransactionBuilder(notary)
                    .addInputState(inputGameStateAndRef)
                    .addCommand(rejectGameCommand)
                    .addOutputState(outputGameState, GameContract.ID)

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

            return subFlow(FinalityFlow(fullySignedTx, listOf(participantFlows), AcceptGameFlow.Initiator.Companion.FINALIZING_TRANSACTION.childProgressTracker()))
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