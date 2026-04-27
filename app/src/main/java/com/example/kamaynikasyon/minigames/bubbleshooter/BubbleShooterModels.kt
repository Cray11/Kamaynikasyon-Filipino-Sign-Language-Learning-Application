package com.example.kamaynikasyon.minigames.bubbleshooter

import android.graphics.Color
import kotlin.math.sqrt

data class BallType(
    val id: Int,
    val color: Int,
    val name: String,
    val letter: String = ""
) {
    companion object {
        val BALL_TYPES = listOf(
            BallType(0, Color.TRANSPARENT, "Empty", ""),
            BallType(1, Color.RED, "Red", ""),
            BallType(2, Color.BLUE, "Blue", ""),
            BallType(3, Color.GREEN, "Green", ""),
            BallType(4, Color.YELLOW, "Yellow", ""),
            BallType(5, Color.MAGENTA, "Magenta", ""),
            BallType(6, Color.CYAN, "Cyan", "")
        )

        fun getBallType(id: Int): BallType = BALL_TYPES.find { it.id == id } ?: BALL_TYPES[0]
        fun getBallTypeWithLetter(id: Int, letter: String): BallType = getBallType(id).copy(letter = letter)
    }
}

data class Ball(
    val centerX: Float,
    val centerY: Float,
    val ballType: BallType,
    val radius: Float,
    val isMoving: Boolean = false,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val isFalling: Boolean = false,
    val fallStartTime: Long = 0L
) {
    fun distanceTo(other: Ball): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

data class GridPosition(val row: Int, val col: Int) {
    companion object {
        fun toScreenPosition(row: Int, col: Int, ballRadius: Float, startX: Float, startY: Float): Pair<Float, Float> {
            val horizontalSpacing = ballRadius * 2f
            val verticalSpacing = ballRadius * sqrt(3f)
            val offsetBalls = if (row % 2 == 0) 2f else 1.5f
            val offsetX = offsetBalls * horizontalSpacing
            val x = startX + col * horizontalSpacing + offsetX - ballRadius
            val y = startY + row * verticalSpacing
            return Pair(x, y)
        }

        fun getNeighbors(row: Int, col: Int): List<Pair<Int, Int>> {
            return if (row % 2 == 0) {
                listOf(
                    Pair(row - 1, col),
                    Pair(row - 1, col + 1),
                    Pair(row, col - 1),
                    Pair(row, col + 1),
                    Pair(row + 1, col),
                    Pair(row + 1, col + 1)
                )
            } else {
                listOf(
                    Pair(row - 1, col - 1),
                    Pair(row - 1, col),
                    Pair(row, col - 1),
                    Pair(row, col + 1),
                    Pair(row + 1, col - 1),
                    Pair(row + 1, col)
                )
            }
        }
    }
}

data class BubbleShooterLevel(
    val id: String,
    val layout: List<String>,
    val ballLetters: Map<String, String> = emptyMap(),
    val ballTypes: List<Int> = emptyList()
) {
    fun getBallAt(row: Int, col: Int): Int {
        if (row < 0 || row >= layout.size) return 0
        val parts = layout[row].split("-")
        if (col < 0 || col >= parts.size) return 0
        return parts[col].toIntOrNull() ?: 0
    }
    fun getGridWidth(): Int = layout.maxOfOrNull { it.split("-").size } ?: 0
    fun getGridHeight(): Int = layout.size
    fun getBallLetter(ballTypeId: Int): String = ballLetters[ballTypeId.toString()] ?: when (ballTypeId) {
        1 -> "A"; 2 -> "B"; 3 -> "C"; else -> ""
    }
}

data class GameState(
    val level: BubbleShooterLevel,
    val movingBalls: List<Ball> = emptyList(),
    val gridBalls: Map<Pair<Int, Int>, Ball> = emptyMap(),
    val emptyPositions: Set<Pair<Int, Int>> = emptySet(),
    val poppingBalls: Map<Pair<Int, Int>, Ball> = emptyMap(),
    val fallingBalls: List<Ball> = emptyList(),
    val currentBall: BallType,
    val predictedLetter: String = "",
    val canShoot: Boolean = false,
    val isGameOver: Boolean = false,
    val isGameWon: Boolean = false,
    val score: Int = 0,
    val originalBallsCount: Int = 0
)


