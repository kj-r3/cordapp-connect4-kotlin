package com.connect4.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameStateTests {
    private val participant: Party = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val initiator: Party = TestIdentity(CordaX500Name("Bob", "London", "GB")).party
    private val initiatorColor: Color = Color.BLACK

    private val boardSize: Pair<Int,Int> = Pair(10,10)
    private val participants: List<AbstractParty> = listOf(initiator,participant)

    @Test
    fun `GameState pending with generated UniqueIdentifier and expected null values`() {
        val gameState = GameState(initiator = initiator,
                initiatorColor = initiatorColor,
                participant = participant,
                boardSize = boardSize,
                status = GameStatus.PENDING)

        assertEquals(initiator, gameState.initiator)
        assertEquals(participant, gameState.participant)
        assertEquals(initiatorColor, gameState.initiatorColor)
        assertEquals(GameStatus.PENDING, gameState.status)
        assertEquals(participants, gameState.participants)
        assertEquals(boardSize, gameState.boardSize)
        assertNotNull(gameState.linearId)
        assertNull(gameState.participantColor)
        assertNull(gameState.victor)
    }

    @Test
    fun `GameState rejected with generated UniqueIdentifier and expected null values`() {
        val gameState = GameState(initiator = initiator,
                initiatorColor = initiatorColor,
                participant = participant,
                boardSize = boardSize,
                status = GameStatus.REJECTED)

        assertEquals(initiator, gameState.initiator)
        assertEquals(participant, gameState.participant)
        assertEquals(initiatorColor, gameState.initiatorColor)
        assertEquals(GameStatus.REJECTED, gameState.status)
        assertEquals(participants, gameState.participants)
        assertEquals(boardSize, gameState.boardSize)
        assertNotNull(gameState.linearId)
        assertNull(gameState.participantColor)
        assertNull(gameState.victor)
    }

    @Test
    fun `GameState accepted with passed UniqueIdentifier and expected values`() {
        val linearId = UniqueIdentifier()
        val participantColor: Color = Color.RED

        val gameState = GameState(linearId = linearId,
                initiator = initiator,
                initiatorColor = initiatorColor,
                participant = participant,
                participantColor =  participantColor,
                boardSize = boardSize,
                status = GameStatus.ACCEPTED)

        assertEquals(linearId, gameState.linearId)
        assertEquals(initiator, gameState.initiator)
        assertEquals(participant, gameState.participant)
        assertEquals(initiatorColor, gameState.initiatorColor)
        assertEquals(participantColor, gameState.participantColor)
        assertEquals(GameStatus.ACCEPTED, gameState.status)
        assertEquals(participants, gameState.participants)
        assertEquals(boardSize, gameState.boardSize)
        assertNull(gameState.victor)

    }

    @Test
    fun `GameState active with passed UniqueIdentifier and expected values`() {
        val linearId = UniqueIdentifier()
        val participantColor: Color = Color.RED

        val gameState = GameState(linearId = linearId,
                initiator = initiator,
                initiatorColor = initiatorColor,
                participant = participant,
                participantColor =  participantColor,
                boardSize = boardSize,
                status = GameStatus.ACTIVE)

        assertEquals(linearId, gameState.linearId)
        assertEquals(initiator, gameState.initiator)
        assertEquals(participant, gameState.participant)
        assertEquals(initiatorColor, gameState.initiatorColor)
        assertEquals(participantColor, gameState.participantColor)
        assertEquals(GameStatus.ACTIVE, gameState.status)
        assertEquals(participants, gameState.participants)
        assertEquals(boardSize, gameState.boardSize)
        assertNull(gameState.victor)

    }

    @Test
    fun `GameState completed with passed UniqueIdentifier and expected values`() {
        val linearId = UniqueIdentifier()
        val participantColor: Color = Color.RED

        val gameState = GameState(linearId = linearId,
                initiator = initiator,
                initiatorColor = initiatorColor,
                participant = participant,
                participantColor =  participantColor,
                boardSize = boardSize,
                victor = initiator,
                status = GameStatus.COMPLETE)

        assertEquals(linearId, gameState.linearId)
        assertEquals(initiator, gameState.initiator)
        assertEquals(participant, gameState.participant)
        assertEquals(initiatorColor, gameState.initiatorColor)
        assertEquals(participantColor, gameState.participantColor)
        assertEquals(GameStatus.COMPLETE, gameState.status)
        assertEquals(participants, gameState.participants)
        assertEquals(boardSize, gameState.boardSize)
        assertEquals(initiator, gameState.victor)

    }
}