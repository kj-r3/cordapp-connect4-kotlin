package com.connect4.states

import com.connect4.contracts.BoardContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(BoardContract::class)
data class BoardState(override val linearId: UniqueIdentifier = UniqueIdentifier(),
                      val gameState: UniqueIdentifier,
                      val boardSize: Pair<Int, Int>,
                      val initiator: Party,
                      val participant: Party,
                      val nextTurn: Party = initiator,
                      val moveNumber: Int = 1,
                      val status: GameStatus = GameStatus.ACTIVE,
                      val boardMap: Map<Int,Cell>? = mapOf(),
                      override val participants: List<AbstractParty> = listOf(initiator, participant)) : LinearState