package com.connect4.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BoardStateTests {
    private val participant: Party = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val initiator: Party = TestIdentity(CordaX500Name("Bob", "London", "GB")).party
    private val gameState: UniqueIdentifier = UniqueIdentifier()
    private val boardSize: Pair<Int,Int> = Pair(10,10)

    @Test
    fun `BoardState active with generated UniqueIdentifier and expected default values`() {
        val boardState = BoardState(gameState = gameState,
                boardSize = boardSize,
                initiator =  initiator,
                participant = participant)

        assertEquals(gameState, boardState.gameState)
        assertEquals(initiator, boardState.initiator)
        assertEquals(participant, boardState.participant)
        assertEquals(initiator, boardState.nextTurn)
        assertEquals(1, boardState.moveNumber)
        assertEquals(GameStatus.ACTIVE, boardState.status)
        assertEquals(listOf(initiator,participant), boardState.participants)
        assertNotNull(boardState.linearId)
        assertNotNull(boardState.boardMap)
    }

    @Test
    fun `BoardState active with generated UniqueIdentifier with no null values`() {
        val cells = listOf(Cell(1, 1, initiator), Cell(2,1,participant))
        val moveNumber = 1
        val linearId = UniqueIdentifier()

        val boardMap = (moveNumber..cells.size).map { i -> i to cells[i-1] }.toMap()

        val boardState = BoardState(linearId = linearId,
                gameState = gameState,
                boardSize = boardSize,
                initiator =  initiator,
                participant = participant,
                boardMap = boardMap,
                nextTurn = initiator,
                moveNumber = boardMap.size,
                status = GameStatus.ACTIVE
        )

        assertEquals(linearId, boardState.linearId)
        assertEquals(gameState, boardState.gameState)
        assertEquals(initiator, boardState.initiator)
        assertEquals(participant, boardState.participant)
        assertEquals(initiator, boardState.nextTurn)
        assertEquals(boardMap, boardState.boardMap)
        assertEquals(boardMap.size, boardState.moveNumber)
        assertEquals(GameStatus.ACTIVE, boardState.status)
        assertEquals(listOf(initiator,participant), boardState.participants)
    }



}