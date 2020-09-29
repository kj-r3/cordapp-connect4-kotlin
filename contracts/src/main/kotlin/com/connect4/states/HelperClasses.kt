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
data class Cell(val column: Int, val row: Int, val occupant: Party) {
    fun getPosition(): Pair<Int,Int> = Pair(column,row)
}

fun Iterable<Map<Int,Cell>>.getNextRowPositionByColumn(column: Int) =
        (flatMap{ it.values }.filter{ it.column == column }.maxBy { it.row }?.row ?: 0) + 1

fun getCellOccupantByPosition(cells: List<Cell>, column: Int, row: Int): Party? {
    cells.forEach { if(it.column==column && it.row==row) return it.occupant }
    return null
}