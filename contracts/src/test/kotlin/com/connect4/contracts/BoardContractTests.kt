package com.connect4.contracts

import com.connect4.states.BoardState
import com.connect4.states.Color
import com.connect4.states.GameState
import com.connect4.states.GameStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test

class BoardContractTests {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carl = TestIdentity(CordaX500Name("Carl", "New York", "US")).party
    private val pendingGame = GameState(initiator = alice,
            participant = bob,
            initiatorColor = Color.RED,
            boardSize = Pair(10,10),
            status = GameStatus.PENDING )
    private val newBoard = BoardState(gameState = pendingGame.linearId,
            boardSize = pendingGame.boardSize,
            initiator = pendingGame.initiator,
            participant = pendingGame.participant,
            moveNumber = 1,
            status = GameStatus.ACTIVE )
    private val acceptedGame = GameState(pendingGame.linearId,
            pendingGame.initiator,
            pendingGame.participant,
            pendingGame.initiatorColor,
            Color.BLACK,
            pendingGame.boardSize,
            newBoard.linearId,
            GameStatus.ACCEPTED )

    @Test
    fun `transaction must include a BoardContract command`() {
        ledgerServices.transaction{
            output(BoardContract.ID, newBoard)
            command(bob.owningKey, DummyContract.Commands.Create())
            `fails with`("Required com.connect4.contracts.BoardContract.Commands command")
            command(bob.owningKey, BoardContract.Commands.New())
            verifies()
        }
    }

    @Test
    fun `New transaction must have no inputs`() {
        ledgerServices.transaction {
            input(BoardContract.ID, newBoard)
            output(BoardContract.ID, newBoard)
            command(bob.owningKey,BoardContract.Commands.New())
            `fails with`("No previous board can be consumed, in inputs, when creating a new board.")
        }
    }

    @Test
    fun `New transaction must have output`() {
        ledgerServices.transaction {
            output(BoardContract.ID, DummyState())
            command(bob.owningKey,BoardContract.Commands.New())
            `fails with`("There should be one board as an output.")
        }
    }

    @Test
    fun `New board must have initiator as nextTurn, and move number 1`() {
        ledgerServices.transaction {
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.initiator,
                    2,
                    newBoard.status))
            command(bob.owningKey,BoardContract.Commands.New())
            `fails with`("The board should start with move number 1")
        }
        ledgerServices.transaction {
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber,
                    newBoard.status))
            command(bob.owningKey,BoardContract.Commands.New())
            `fails with`("The first move should be by the initiator")
        }
    }
}