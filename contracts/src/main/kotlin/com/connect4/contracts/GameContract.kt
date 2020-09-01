package com.connect4.contracts

import com.connect4.states.Color
import com.connect4.states.GameState
import com.connect4.states.GameStatus
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

// ************
// * Contract *
// ************
class GameContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.connect4.contracts.GameContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val inputs = tx.inputsOfType<GameState>()
        val outputs = tx.outputsOfType<GameState>()

        when(command.value) {
            is Commands.NewGame -> requireThat {
                //Constraints on the shape of the transaction
                "No previous game can be consumed, in inputs, when creating a new game." using inputs.isEmpty()
                "There should be one game as an output." using (outputs.size == 1)

                //Constraints on the shape of the GameState
                "There should be two distinct participants in the game." using (outputs[0].participants.distinct().count() == 2)
                "The game should be pending acceptance by the Participant." using (outputs[0].status == GameStatus.PENDING)
                "The board size cannot exceed 10x10." using (outputs[0].boardSize.first <= 10 && outputs[0].boardSize.second <= 10)
                "The board size needs to be at least 4x4." using (outputs[0].boardSize.first >= 4 && outputs[0].boardSize.second >= 4)

                // Constraints on the signers.
                "The initiator should sign." using command.signers.containsAll(outputs.map { it.initiator.owningKey }.distinct())

            }
            is Commands.AcceptGame -> requireThat {
                //Constraints on the shape of the transaction
                "One game can be consumed, in inputs, when accepting a new game." using (inputs.size == 1)
                "There should be one game as an output." using (outputs.size == 1)

                //Constraints on the shape of the GameState
                "There should be two distinct participants in the game." using (outputs[0].participants.distinct().count() == 2)
                "The game should be accepted by the Participant." using (outputs[0].status == GameStatus.ACCEPTED)
                "The Participant should select a color." using (Color.values().contains(outputs[0].participantColor) && outputs[0].participantColor != outputs[0].initiatorColor)
                "The the board issued should be referenced." using (outputs[0].boardState != null)

                // Constraints on the signers.
                "The participant should sign." using command.signers.containsAll(outputs.map { it.participant.owningKey }.distinct())

            }
            is Commands.RejectGame -> requireThat {
                //Constraints on the shape of the transaction
                "One game can be consumed, in inputs, when rejecting a new game." using (inputs.size == 1)
                "There should be one game as an output." using (outputs.size == 1)

                //Constraints on the shape of the GameState
                "There should be two distinct participants in the game." using (outputs[0].participants.distinct().count() == 2)
                "The game should be rejected by the Participant." using (outputs[0].status == GameStatus.REJECTED)
                "The Participant should select a color." using (Color.values().contains(outputs[0].participantColor))

                // Constraints on the signers.
                "The participant should sign." using command.signers.containsAll(outputs.map { it.participant.owningKey }.distinct())
            }
            is Commands.CompleteGame -> requireThat {
                //Constraints on the shape of the transaction
                "One game can be consumed, in inputs, when completing a new game." using (inputs.size == 1)
                "There should be one game as an output." using (outputs.size == 1)

                //Constraints on the shape of the GameState
                "There should be two distinct participants in the game." using (outputs[0].participants.distinct().count() == 2)
                when (outputs[0].status) {
                    GameStatus.COMPLETE ->
                        "The game should declare a victor when completed." using (outputs[0].victor != null)
                    GameStatus.DRAW ->
                        "The game should not declare a victor when a draw." using (outputs[0].victor == null)
                    else -> throw IllegalArgumentException("You can only complete a game if there is a victor or a draw.")
                }
                "The game should be in a complete state with a victor." using (outputs[0].status == GameStatus.COMPLETE)

                // Constraints on the signers.
                "The both initiator and participant should sign." using command.signers.containsAll(outputs[0].participants.map { it.owningKey }.distinct())
            }
            else -> throw IllegalArgumentException("Unknown command ${command.value}.")
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class NewGame : Commands
        class RejectGame : Commands
        class AcceptGame : Commands
        class CompleteGame : Commands
    }
}