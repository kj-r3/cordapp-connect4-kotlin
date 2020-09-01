package com.connect4.states

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class GameStatus {
    PENDING,ACCEPTED,REJECTED,ACTIVE,COMPLETE,DRAW
}

@CordaSerializable
enum class Color {
    RED,BLUE,YELLOW,BLACK  
}

@CordaSerializable
data class Cell(val i: Int, val j: Int, val occupant: Party)

