package com.connect4

import com.connect4.flows.*
import com.connect4.states.GameState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith

class NewGameFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.connect4.contracts"),
            TestCordapp.findCordapp("com.connect4.flows")
    )))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(NewGameFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `new Game issued`() {
        val flow = NewGameFlow.Initiator(b.info.legalIdentities[0], "RED", 10, 10)
        val future = a.startFlow(flow)
        network.runNetwork()

        val tx = future.getOrThrow()
        assert(tx.tx.outputStates.size==1)
        assert(tx.tx.outRefsOfType(GameState::class.java).isNotEmpty())
    }

    @Test
    fun `new Game cannot be with same parties`() {
        val flow = NewGameFlow.Initiator(b.info.legalIdentities[0], "RED", 10, 10)
        val future = b.startFlow(flow)
        network.runNetwork()

        assertFailsWith<TransactionVerificationException>{ future.getOrThrow()}
    }

    @Test
    fun `new Game board size cannot exceed 10 x 10`() {
        val flow = NewGameFlow.Initiator(b.info.legalIdentities[0], "RED", 11, 10)
        val future = a.startFlow(flow)
        network.runNetwork()

        assertFailsWith<TransactionVerificationException>{ future.getOrThrow()}
    }
}
