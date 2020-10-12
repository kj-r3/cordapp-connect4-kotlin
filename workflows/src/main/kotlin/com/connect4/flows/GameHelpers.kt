package com.connect4.flows

import com.connect4.states.Cell
import com.connect4.states.GameStatus
import com.connect4.states.getCellOccupantByPosition
import net.corda.core.identity.Party

object GameValidator {

    fun verifyGameProgress(boardMap: Map<Int,Cell>, boardSize: Pair<Int, Int>): Pair<GameStatus, Party?> {

        val gameFinished = boardMap.size == boardSize.first * boardSize.second
        val straightWin = checkStraightWin(boardMap.entries.map { it.value }.toList(), boardSize)
        val diagonalWin = checkDiagonalWin(boardMap.entries.map { it.value }.toList(), boardSize)

        if(straightWin== null && diagonalWin== null && gameFinished){
            return Pair(GameStatus.DRAW, null)

        } else if (straightWin!=null) {
            return Pair(GameStatus.COMPLETE, straightWin)
        } else if (diagonalWin!=null) {
            return Pair(GameStatus.COMPLETE, diagonalWin)
        }

        return Pair(GameStatus.ACTIVE, null)
    }

    private fun checkStraightWin (boardCells: List<Cell>, boardSize: Pair<Int,Int>): Party? {
        val lastColumnValues = mutableMapOf<Int, MutableList<Party>>()
        val lastRowValues = mutableMapOf<Int, MutableList<Party>>()

        for (column in 0 until boardSize.first){
            for (row in 0 until boardSize.second) {
                val occupant = getCellOccupantByPosition(boardCells, column, row)

                if(occupant!=null){
                    lastColumnValues.add(column,occupant)
                    lastRowValues.add(row,occupant)
                }

                if(lastColumnValues.has4Match(column) || lastRowValues.has4Match(row))
                    return occupant
            }
        }
        return null
    }

    private fun checkDiagonalWin (boardCells: List<Cell>, boardSize: Pair<Int,Int>): Party? {
        val BOTTOM_LEFT = 0
        val BOTTOM_RIGHT = 1
        val TOP_LEFT = 2
        val TOP_RIGHT = 3

        for (i in 0 until boardSize.first){
            val cornerValues = mutableMapOf<Int, MutableList<Party>>()
            val maxIndex = boardSize.first - 1

            var occupant = getCellOccupantByPosition(boardCells, 0, i)
            if (occupant != null ) cornerValues.add(BOTTOM_LEFT, occupant)

            occupant = getCellOccupantByPosition(boardCells, maxIndex, maxIndex-1)
            if (occupant != null ) cornerValues.add(BOTTOM_RIGHT, occupant)

            occupant = getCellOccupantByPosition(boardCells, 0, maxIndex-1)
            if (occupant != null ) cornerValues.add(TOP_LEFT, occupant)

            occupant = getCellOccupantByPosition(boardCells, maxIndex-1, 0)
            if (occupant != null ) cornerValues.add(TOP_RIGHT, occupant)

            for(j in 1..i){
                occupant = getCellOccupantByPosition(boardCells, j, i-j)
                if (occupant != null ) cornerValues.add(BOTTOM_LEFT, occupant)

                occupant = getCellOccupantByPosition(boardCells, maxIndex - j, (maxIndex - i) + j)
                if (occupant != null ) cornerValues.add(BOTTOM_RIGHT, occupant)

                occupant = getCellOccupantByPosition(boardCells, j, (maxIndex - i) + j)
                if (occupant != null ) cornerValues.add(TOP_LEFT, occupant)

                occupant = getCellOccupantByPosition(boardCells, (maxIndex - i) + j, j)
                if (occupant != null ) cornerValues.add(TOP_RIGHT, occupant)
            }

            for (corner in 0 until 4) {
                if (cornerValues.has4Match(corner)) {
                    val positions = cornerValues[corner]?.toList()?.takeLast(4)!!
                    return positions[0]
                }
            }

        }
        return null
    }

    private fun <K, V> MutableMap<K, MutableList<V>>.add(k: K, v: V) = get(k)?.add(v)
            ?: put(k, mutableListOf(v))

    private fun MutableMap<Int, MutableList<Party>>.has4Match(k: Int): Boolean {
        val lastOccupants = get(k)?.takeLast(4) ?: listOf()
        if (lastOccupants.size < 4) {
            return false
        }

        for ((index, occupant) in lastOccupants.withIndex()) {
            if (index == 0) continue
            val previousPositionOccupant = lastOccupants[index - 1]
            if (previousPositionOccupant != occupant || occupant == null ) {
                return false
            }
        }
        return true
    }
}

