package com.connect4.contracts

import com.connect4.states.BoardState
import com.connect4.states.GameState
import com.connect4.states.GameStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

// ************
// * Contract *
// ************
class BoardContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.connect4.contracts.BoardContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val inputs = tx.inputsOfType<BoardState>()
        val outputs = tx.outputsOfType<BoardState>()

        when(command.value) {
            is Commands.New -> requireThat {
                //Constraints on the shape of the transaction
                "No previous board can be consumed, in inputs, when creating a new board." using inputs.isEmpty()
                "There should be one board as an output." using (outputs.size == 1)

                //Constraints on the shape of the BoardState
                "The first move should be by the initiator" using (outputs[0].nextTurn == outputs[0].initiator)
                "The board should start with move number 1" using (outputs[0].moveNumber == 1)

                // Constraints on the signers.
                "The participant should sign." using command.signers.containsAll(outputs.map { it.participant.owningKey }.distinct())

            }
            is Commands.Play -> requireThat {
                //Constraints on the shape of the transaction
                "One board can be consumed, in inputs, when making a move." using (inputs.size == 1)
                "There should be one board as an output." using (outputs.size == 1)

                //Constraints on the shape of the BoardState
                "Cannot have two moves on the same cell." using (!inputs[0].boardMap!!.mapValues{ it.value }.containsValue(outputs [0].boardMap!![outputs[0].moveNumber]))
                "A player cannot have two consecutive turns." using(inputs[0].nextTurn != outputs[0].nextTurn)
                "The next turn must belong to one of the participants." using(outputs[0].participants.contains(outputs[0].nextTurn))
                "Board state must remain active." using(outputs[0].status == GameStatus.ACTIVE)
                "Move number must increment after each play." using(outputs[0].moveNumber == inputs[0].moveNumber + 1)

                // Constraints on the signers.
                "The player making the move should sign." using command.signers.containsAll(inputs.map { it.nextTurn.owningKey }.distinct())

            }
            is Commands.WinningPlay -> requireThat {
                //Constraints on the shape of the transaction
                "One board can be consumed, in inputs, when making a move." using (inputs.size == 1)
                "There should be one board as an output." using (outputs.size == 1)

                //Constraints on the shape of the BoardState
                "Cannot have two moves on the same cell." using (!inputs[0].boardMap!!.mapValues{ it.value }.containsValue(outputs [0].boardMap!![outputs[0].moveNumber]))
                "Board state must be completed when winning play is detected." using(outputs[0].status == GameStatus.COMPLETE)

                // Constraints on the signers.
                "The both players should sign the end of the game." using command.signers.containsAll(outputs[0].participants.map { it.owningKey }.distinct())

            }
            is Commands.Draw -> requireThat {
                //Constraints on the shape of the transaction
                "One board can be consumed, in inputs, when accepting a new game." using (inputs.size == 1)
                "There should be one board as an output." using (outputs.size == 1)

                "Board state must be completed when a draw is detected." using(outputs[0].status == GameStatus.COMPLETE)

                // Constraints on the signers.
                "The player making the move should sign." using command.signers.containsAll(outputs[0].participants.map { it.owningKey }.distinct())

            }
            else -> throw IllegalArgumentException("Unknown command ${command.value}.")
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class New : Commands
        class Play : Commands
        class WinningPlay : Commands
        class Draw : Commands
    }
}