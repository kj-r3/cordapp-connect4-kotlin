package com.connect4

import com.connect4.flows.AcceptGameFlow
import com.connect4.flows.NewGameFlow
import com.connect4.flows.RejectGameFlow
import com.connect4.states.BoardState
import com.connect4.states.GameState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AcceptGameFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.connect4.contracts"),
            TestCordapp.findCordapp("com.connect4.flows")
    )))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(NewGameFlow.Responder::class.java)
            it.registerInitiatedFlow(AcceptGameFlow.Responder::class.java)
            it.registerInitiatedFlow(RejectGameFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `Accept Game with all outputs`() {
        val flow = AcceptGameFlow.Initiator(a.issueNewGame(network, b.info.legalIdentities[0])[0].state.data.linearId, "BLACK")
        val future = b.startFlow(flow)
        network.runNetwork()

        val tx = future.getOrThrow()
        assert(tx.tx.outputStates.size==2)
        assert(tx.tx.outRefsOfType(GameState::class.java).isNotEmpty())
        assert(tx.tx.outRefsOfType(BoardState::class.java).isNotEmpty())
        val gameStates = a.services.vaultService.queryBy(GameState::class.java).states
        val boardStates = a.services.vaultService.queryBy(BoardState::class.java).states
        assertEquals(gameStates.size, 1)
        assertEquals(boardStates.size, 1)
    }

    @Test
    fun `Accept Game fails with same color`() {
        val flow = AcceptGameFlow.Initiator(a.issueNewGame(network, b.info.legalIdentities[0])[0].state.data.linearId, "RED")
        val future = b.startFlow(flow)
        network.runNetwork()

        assertFailsWith<TransactionVerificationException>{ future.getOrThrow()}
    }

    @Test
    fun `Cannot accept game that has been rejected`() {
        val flow = AcceptGameFlow.Initiator(b.issueRejectedGame(network, b.info.legalIdentities[0], a)[0].state.data.linearId, "RED")
        val future = b.startFlow(flow)
        network.runNetwork()

        assertFailsWith<TransactionVerificationException>{ future.getOrThrow()}
    }
}