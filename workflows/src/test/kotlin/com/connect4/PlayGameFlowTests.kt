package com.connect4

import com.connect4.flows.AcceptGameFlow
import com.connect4.flows.NewGameFlow
import com.connect4.flows.PlayGameFlow
import com.connect4.states.BoardState
import com.connect4.states.GameState
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PlayGameFlowTests {

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
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `Play first move with all outputs`() {
        val flow = PlayGameFlow.Initiator(b.issueAcceptedGame(network, b.info.legalIdentities[0], a)[0].state.data.linearId, 1)
        val future = a.startFlow(flow)
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
    fun `Game completed with straight win`() {
        val gameId = b.issueAcceptedGame(network, b.info.legalIdentities[0], a)[0].state.data.linearId
        var completeGameTx: SignedTransaction? = null

        for(i in 1..7){
            val player = if (i % 2 == 0) b else a
            val column = if(player == a) 1 else 2
            val flow = PlayGameFlow.Initiator(gameId, column)
            val future = player.startFlow(flow)
            network.runNetwork()
            val tx = future.getOrThrow()

            assert(tx.tx.outRefsOfType(BoardState::class.java).isNotEmpty())

            if(i==7) completeGameTx = tx
        }

        assert(completeGameTx!!.tx.outputStates.size==2)
        assert(completeGameTx.tx.outRefsOfType(GameState::class.java).isNotEmpty())
        assert(completeGameTx.tx.outRefsOfType(BoardState::class.java).isNotEmpty())
        val gameState = a.services.vaultService.queryBy(GameState::class.java).states
        assertEquals(gameState[0].state.data.victor, a.info.legalIdentities[0])
    }

    @Test
    fun `Game completed with diagonal win`() {
        val gameId = b.issueAcceptedGame(network, b.info.legalIdentities[0], a)[0].state.data.linearId
        var completeGameTx: SignedTransaction? = null

        for(i in 1..11){
            val player = if (i % 2 == 0) b else a
            val column = if(player == a){
                when(i){
                    1->1
                    3->2
                    5->4
                    7->3
                    9->5
                    11->4
                    else->i
                }
            }  else {
                when(i){
                    2->2
                    4->3
                    6->3
                    8->4
                    10->4
                    else->i
                }
            }
            val flow = PlayGameFlow.Initiator(gameId, column)
            val future = player.startFlow(flow)
            network.runNetwork()
            val tx = future.getOrThrow()

            assert(tx.tx.outRefsOfType(BoardState::class.java).isNotEmpty())

            if(i==11) completeGameTx = tx
        }

        assert(completeGameTx!!.tx.outputStates.size==2)
        assert(completeGameTx.tx.outRefsOfType(GameState::class.java).isNotEmpty())
        assert(completeGameTx.tx.outRefsOfType(BoardState::class.java).isNotEmpty())
        val gameState = a.services.vaultService.queryBy(GameState::class.java).states
        assertEquals(gameState[0].state.data.victor, a.info.legalIdentities[0])
    }
}