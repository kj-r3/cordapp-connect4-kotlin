package com.connect4

import com.connect4.flows.AcceptGameFlow
import com.connect4.flows.NewGameFlow
import com.connect4.flows.PlayGameFlow
import com.connect4.flows.RejectGameFlow
import com.connect4.states.BoardState
import com.connect4.states.GameState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
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

fun StartedMockNode.issueRejectedGame(network: MockNetwork, participant: Party, initiator: StartedMockNode): List<StateAndRef<GameState>> {

    val newGameRef = initiator.issueNewGame(network, participant)

    return RejectGameFlow.Initiator(newGameRef[0].state.data.linearId)
            .let { startFlow(it) }
            .also { network.runNetwork() }
            .getOrThrow()
            .toLedgerTransaction(services)
            .outRefsOfType<GameState>()
}

fun StartedMockNode.issueAcceptedGame(network: MockNetwork, participant: Party, initiator: StartedMockNode): List<StateAndRef<GameState>> {

    val newGameRef = initiator.issueNewGame(network, participant)

    return AcceptGameFlow.Initiator(newGameRef[0].state.data.linearId, "BLACK")
            .let { startFlow(it) }
            .also { network.runNetwork() }
            .getOrThrow()
            .toLedgerTransaction(services)
            .outRefsOfType<GameState>()
}

fun StartedMockNode.makeMove(network: MockNetwork, gameRef: UniqueIdentifier, column: Int): List<StateAndRef<BoardState>> {

    return PlayGameFlow.Initiator(gameRef, column)
            .let { startFlow(it) }
            .also { network.runNetwork() }
            .getOrThrow()
            .toLedgerTransaction(services)
            .outRefsOfType<BoardState>()
}