package com.connect4.contracts

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

class GameContractTests {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carl = TestIdentity(CordaX500Name("Carl", "New York", "US")).party

    @Test
    fun `transaction must include a GameContract command`() {
        ledgerServices.transaction{
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,DummyContract.Commands.Create())
            `fails with`("Required com.connect4.contracts.GameContract.Commands command")
            command(alice.owningKey,GameContract.Commands.NewGame())
            verifies()
        }
    }

    @Test
    fun `NewGame transaction must have no inputs`() {
        ledgerServices.transaction {
            input(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("No previous game can be consumed, in inputs, when creating a new game.")
        }
    }

    @Test
    fun `NewGame transaction must have output`() {
        ledgerServices.transaction {
            output(GameContract.ID, DummyState())
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("There should be one game as an output.")
        }
    }

    @Test
    fun `NewGame transaction must have distinct participants`() {
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = alice,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("There should be two distinct participants in the game.")
        }
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            verifies()
        }
    }

    @Test
    fun `NewGame transaction must have pending status`() {
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.ACTIVE ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("The game should be pending acceptance by the Participant.")
        }
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            verifies()
        }
    }

    @Test
    fun `NewGame board size within limits`() {
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(11,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("The board size cannot exceed 10x10.")
        }
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(2,2),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            `fails with`("The board size needs to be at least 4x4.")
        }
        ledgerServices.transaction {
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            verifies()
        }
    }

    @Test
    fun `NewGame transaction must be signed by initiator`() {
        ledgerServices.transaction{
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(bob.owningKey,GameContract.Commands.NewGame())
            `fails with`("The initiator should sign.")
        }
        ledgerServices.transaction{
            output(GameContract.ID, GameState(initiator = alice,
                    participant = bob,
                    initiatorColor = Color.RED,
                    boardSize = Pair(10,10),
                    status = GameStatus.PENDING ))
            command(alice.owningKey,GameContract.Commands.NewGame())
            verifies()
        }
    }

    @Test
    fun `AcceptGame transaction must have correct inputs and outputs`() {
        val gameState = GameState(initiator = alice,
                participant = bob,
                initiatorColor = Color.RED,
                boardSize = Pair(10,10),
                status = GameStatus.PENDING)

        ledgerServices.transaction {
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    Color.BLACK,
                    gameState.boardSize,
                    UniqueIdentifier(),
                    GameStatus.ACCEPTED ))
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("One game can be consumed, in inputs, when accepting a new game.")
            input(GameContract.ID, gameState)
            verifies()
        }

        ledgerServices.transaction {
            input(GameContract.ID, gameState)
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("There should be one game as an output.")
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    Color.BLACK,
                    gameState.boardSize,
                    UniqueIdentifier(),
                    GameStatus.ACCEPTED ))
            verifies()
        }

    }

    @Test
    fun `AcceptGame transaction must be in ACCEPTED status`() {
        val gameState = GameState(initiator = alice,
                participant = bob,
                initiatorColor = Color.RED,
                boardSize = Pair(10,10),
                status = GameStatus.PENDING)

        ledgerServices.transaction {
            input(GameContract.ID, gameState)
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    Color.BLACK,
                    gameState.boardSize,
                    UniqueIdentifier(),
                    GameStatus.ACTIVE ))
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("The game should be accepted by the Participant.")
        }
    }

    @Test
    fun `AcceptGame transaction must include participant color and board`() {
        val gameState = GameState(initiator = alice,
                participant = bob,
                initiatorColor = Color.RED,
                boardSize = Pair(10,10),
                status = GameStatus.PENDING)

        ledgerServices.transaction {
            input(GameContract.ID, gameState)
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    null,
                    gameState.boardSize,
                    UniqueIdentifier(),
                    GameStatus.ACCEPTED ))
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("The Participant should select a color.")
        }
        ledgerServices.transaction {
            input(GameContract.ID, gameState)
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    gameState.initiatorColor,
                    gameState.boardSize,
                    UniqueIdentifier(),
                    GameStatus.ACCEPTED ))
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("The Participant should select a color.")
        }
        ledgerServices.transaction {
            input(GameContract.ID, gameState)
            output(GameContract.ID, GameState(gameState.linearId,
                    gameState.initiator,
                    gameState.participant,
                    gameState.initiatorColor,
                    Color.BLACK,
                    gameState.boardSize,
                    null,
                    GameStatus.ACCEPTED ))
            command(bob.owningKey,GameContract.Commands.AcceptGame())
            `fails with`("The the board issued should be referenced.")
        }
    }
}