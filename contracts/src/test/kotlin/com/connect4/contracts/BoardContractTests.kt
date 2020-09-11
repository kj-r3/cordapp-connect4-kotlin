package com.connect4.contracts

import com.connect4.states.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.coretesting.internal.participant
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

    @Test
    fun `New transaction must have participant signature`() {
        ledgerServices.transaction {
            output(BoardContract.ID, newBoard)
            command(alice.owningKey,BoardContract.Commands.New())
            `fails with`("The participant should sign.")
        }
    }

    @Test
    fun `New transaction must with correct outputs`() {
        ledgerServices.transaction{
            output(BoardContract.ID, newBoard)
            command(bob.owningKey, BoardContract.Commands.New())
            verifies()
        }
    }

    @Test
    fun `Play transaction with correct inputs and outputs`() {
        ledgerServices.transaction {
            input(BoardContract.ID, newBoard)
            output(BoardContract.ID, BoardState(newBoard.linearId,
            newBoard.gameState,
            newBoard.boardSize,
            newBoard.initiator,
            newBoard.participant,
            newBoard.participant,
            newBoard.moveNumber+1,
            newBoard.status,
            mapOf(1 to Cell(1,1,alice))))
            command(alice.owningKey,BoardContract.Commands.Play())
            verifies()
        }

        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(2,1,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            verifies()
        }
    }

    @Test
    fun `Play transaction cannot have two moves on same cell`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,1,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("Cannot have two moves on the same cell.")
        }
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            verifies()
        }
    }

    @Test
    fun `Player cannot have two consecutive turns`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    bob,
                    newBoard.moveNumber+2,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("A player cannot have two consecutive turns.")
        }
    }

    @Test
    fun `Next turn must be from participants`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    carl,
                    newBoard.moveNumber+2,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("The next turn must belong to one of the participants.")
        }
    }

    @Test
    fun `Board must remain Active`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.COMPLETE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("Board state must remain active.")
        }
    }

    @Test
    fun `Move number must increment`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("Move number must increment after each play.")
        }
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+3,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(bob.owningKey,BoardContract.Commands.Play())
            failsWith("Move number must increment after each play.")
        }
    }

    @Test
    fun `Player should sign turn`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.ACTIVE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(1,2,bob))))
            command(alice.owningKey,BoardContract.Commands.Play())
            failsWith("The player making the move should sign.")
        }
    }

    @Test
    fun `WinningPlay transaction with correct inputs and outputs`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.COMPLETE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(2,1,bob))))
            command(listOf(bob.owningKey, alice.owningKey),BoardContract.Commands.WinningPlay())
            verifies()
        }
    }

    @Test
    fun `WinningPlay transaction requires both participant signatures`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.COMPLETE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(2,1,bob))))
            command(bob.owningKey,BoardContract.Commands.WinningPlay())
            failsWith("Both players should sign the end of the game.")
        }
    }

    @Test
    fun `Draw transaction with correct inputs and outputs`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.COMPLETE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(2,1,bob))))
            command(listOf(bob.owningKey, alice.owningKey),BoardContract.Commands.Draw())
            verifies()
        }
    }

    @Test
    fun `Draw transaction requires both participant signatures`() {
        ledgerServices.transaction {
            input(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    newBoard.participant,
                    newBoard.moveNumber+1,
                    newBoard.status,
                    mapOf(1 to Cell(1,1,alice))))
            output(BoardContract.ID, BoardState(newBoard.linearId,
                    newBoard.gameState,
                    newBoard.boardSize,
                    newBoard.initiator,
                    newBoard.participant,
                    alice,
                    newBoard.moveNumber+2,
                    GameStatus.COMPLETE,
                    mapOf(1 to Cell(1,1,alice), 2 to Cell(2,1,bob))))
            command(bob.owningKey,BoardContract.Commands.Draw())
            failsWith("Both players should sign the end of the game.")
        }
    }
}