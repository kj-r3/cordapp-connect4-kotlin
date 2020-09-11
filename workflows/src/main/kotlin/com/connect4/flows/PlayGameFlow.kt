package com.connect4.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.flows.Initiator
import net.corda.core.flows.*
import net.corda.core.utilities.ProgressTracker

object PlayGameFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator() : FlowLogic<Unit>() {
        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call() {
            // Initiator flow logic goes here.
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Responder flow logic goes here.
        }
    }
}