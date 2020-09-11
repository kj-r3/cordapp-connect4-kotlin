package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.connect4.contracts.GameContract
import com.connect4.states.Color
import com.connect4.states.GameState
import com.connect4.states.GameStatus.PENDING
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object NewGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val participant: Party,
                    _color: String,
                    columns: Int,
                    rows: Int): FlowLogic<SignedTransaction>() {

        private val color: Color = Color.valueOf(_color)
        private val boardSize: Pair<Int,Int> = Pair(columns,rows)

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating new game based on input.")
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
            val initiator = ourIdentity
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            /*****************************************************
              STEP1 - Generate new Game State and Transaction
            ******************************************************/
            progressTracker.currentStep = GENERATING_TRANSACTION

            val newGameState = GameState(UniqueIdentifier(),initiator,participant,color,boardSize = boardSize, status = PENDING)
            val txCommand = Command(GameContract.Commands.NewGame(), initiator.owningKey)
            val txBuilder = TransactionBuilder(notary).addCommand(txCommand).addOutputState(newGameState, GameContract.ID)
            /******************************************************
               STEP2 - Verifying constraints of the new game
             ******************************************************/
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            /******************************************************
                STEP3 - Sign and finalize new game request
             ******************************************************/
            progressTracker.currentStep = SIGNING_TRANSACTION

            //The initiator is the only party that needs to sign, we then will send the pending game to the participant.
            val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALIZING_TRANSACTION
            val participantFlows = initiateFlow(participant)

            return subFlow(FinalityFlow(fullySignedTx, listOf(participantFlows), FINALIZING_TRANSACTION.childProgressTracker()))

        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val flowSession: FlowSession): FlowLogic<SignedTransaction>(){

        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(flowSession))
        }
    }
}