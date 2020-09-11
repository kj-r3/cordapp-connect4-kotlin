package com.connect4

import com.connect4.flows.NewGameFlow
import com.connect4.states.GameState
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun StartedMockNode.issueNewGame(network: MockNetwork, participant: Party) =
        NewGameFlow.Initiator(participant, "RED", 10, 10)
                .let{startFlow(it)}
                .also{network.runNetwork()}
                .getOrThrow()
                .toLedgerTransaction(services)
                .outRefsOfType<GameState>()