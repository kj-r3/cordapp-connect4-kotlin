package com.connect4

import com.connect4.flows.NewGameFlow
import com.connect4.flows.RejectGameFlow
import com.connect4.states.BoardState
import com.connect4.states.GameState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RejectGameFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.connect4.contracts"),
            TestCordapp.findCordapp("com.connect4.flows")
    )))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(NewGameFlow.Responder::class.java)
            it.registerInitiatedFlow(RejectGameFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `Reject Game with all outputs`() {
        val flow = RejectGameFlow.Initiator(a.issueNewGame(network, b.info.legalIdentities[0])[0].state.data.linearId)
        val future = b.startFlow(flow)
        network.runNetwork()

        val tx = future.getOrThrow()
        assert(tx.tx.outputStates.size==1)
        assert(tx.tx.outRefsOfType(GameState::class.java).isNotEmpty())
        assert(tx.tx.outRefsOfType(BoardState::class.java).isEmpty())
        val gameStates = a.services.vaultService.queryBy(GameState::class.java).states
        val boardStates = a.services.vaultService.queryBy(BoardState::class.java).states
        assertEquals(gameStates.size, 1)
        assertEquals(boardStates.size, 0)
    }

    @Test
    fun `Reject Game fails with no correct reference`() {
        a.issueNewGame(network,b.info.legalIdentities[0])
        val flow = RejectGameFlow.Initiator(UniqueIdentifier())
        val future = b.startFlow(flow)
        network.runNetwork()

        assertFailsWith<NoSuchElementException>{ future.getOrThrow()}
    }
}