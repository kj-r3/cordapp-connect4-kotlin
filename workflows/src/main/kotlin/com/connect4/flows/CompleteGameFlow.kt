package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.connect4.contracts.GameContract
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

object CompleteGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator (private val gameStateId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

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

            /*****************************************************
            STEP1 - Find GameState to accept and generate complete transaction
             ******************************************************/
            progressTracker.currentStep = GENERATING_TRANSACTION
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameStateId))
            val inputGameStateAndRef = serviceHub.vaultService.queryBy<GameState>(queryCriteria).states.single()

            val inputGameState = inputGameStateAndRef.state.data


            val notary = inputGameStateAndRef.state.notary
            val completeGameCommand = Command(GameContract.Commands.CompleteGame(), listOf(inputGameState.participant.owningKey, inputGameState.initiator.owningKey))

            val outputGameState = GameState(inputGameState.linearId,
                    inputGameState.initiator,
                    inputGameState.participant,
                    inputGameState.initiatorColor,
                    inputGameState.participantColor,
                    inputGameState.boardSize,
                    GameStatus.ACCEPTED,
                    inputGameState.victor)

            val txnBuilder = TransactionBuilder(notary)
                    .addInputState(inputGameStateAndRef)
                    .addCommand(completeGameCommand)
                    .addOutputState(outputGameState, GameContract.ID)

            /******************************************************
            STEP2 - Verifying constraints of the game and board
             ******************************************************/
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txnBuilder.verify(serviceHub)

            /******************************************************
            STEP3 - Sign and finalize new game request
             ******************************************************/
            progressTracker.currentStep = NewGameFlow.Initiator.Companion.SIGNING_TRANSACTION

            //The initiator is the only party that needs to sign, we then will send the pending game to the participant.
            val signedTransaction = serviceHub.signInitialTransaction(txnBuilder)

            progressTracker.currentStep = NewGameFlow.Initiator.Companion.FINALIZING_TRANSACTION
            val participantFlow = initiateFlow(outputGameState.participants.filter { ourIdentity.owningKey == it.owningKey }[0])

            return subFlow(FinalityFlow(signedTransaction, listOf(participantFlow), FINALIZING_TRANSACTION.childProgressTracker()))
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