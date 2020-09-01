package com.connect4.states

import com.connect4.contracts.GameContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(GameContract::class)
data class GameState(val linearId: UniqueIdentifier = UniqueIdentifier(),
                     val initiator: Party,
                     val participant: Party,
                     val initiatorColor: Color,
                     val participantColor: Color? = null,
                     val boardSize: Pair<Int,Int>,
                     val boardState: UniqueIdentifier? = null,
                     val status: GameStatus? = null,
                     val victor: Party? = null,
                    override val participants: List<AbstractParty> = listOf(initiator,participant)) : ContractState