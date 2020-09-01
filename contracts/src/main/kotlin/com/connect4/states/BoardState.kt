package com.connect4.states

import com.connect4.contracts.BoardContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import org.checkerframework.common.aliasing.qual.Unique

// *********
// * State *
// *********
@BelongsToContract(BoardContract::class)
data class BoardState(val linearId: UniqueIdentifier = UniqueIdentifier(),
                      val gameState: UniqueIdentifier,
                      val boardSize: Pair<Int, Int>,
                      val initiator: Party,
                      val participant: Party,
                      val nextTurn: Party = initiator,
                      val moveNumber: Int = 1,
                      val status: GameStatus = GameStatus.ACTIVE,
                      val boardMap: Map<Int,Cell>? = null,
                      override val participants: List<AbstractParty> = listOf(initiator, participant)) : ContractState