package com.example.kamaynikasyon.minigames.bubbleshooter

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class BubbleShooterGameEngine {

    private var gameState: GameState? = null
    private var ballRadius = 45f
    private var gridStartX = 0f
    private var gridStartY = 0f
    private val ballSpeed = 2800f

    private var movingBalls = mutableListOf<Ball>()
    private var gridBalls = mutableMapOf<Pair<Int, Int>, Ball>()
    private var emptyPositions = mutableSetOf<Pair<Int, Int>>()
    private var newlyAttachedBalls = mutableSetOf<Pair<Int, Int>>()
    private var poppingBalls = mutableMapOf<Pair<Int, Int>, Ball>()
    private var fallingBalls = mutableListOf<Ball>()
    private var originalBalls = mutableSetOf<Pair<Int, Int>>() // Track original balls from level

    var onGameStateChanged: ((GameState) -> Unit)? = null

    fun setGridConfig(radius: Float, startX: Float, startY: Float) {
        ballRadius = radius
        gridStartX = startX
        gridStartY = startY
    }

    fun initializeGame(level: BubbleShooterLevel) {
        gridBalls.clear()
        emptyPositions.clear()
        newlyAttachedBalls.clear()
        poppingBalls.clear()
        fallingBalls.clear()
        movingBalls.clear()
        originalBalls.clear()

        for (row in 0 until level.getGridHeight()) {
            for (col in 0 until level.getGridWidth()) {
                val id = level.getBallAt(row, col)
                if (id > 0) {
                    val letter = level.getBallLetter(id)
                    val type = BallType.getBallTypeWithLetter(id, letter)
                    val (x, y) = GridPosition.toScreenPosition(row, col, ballRadius, gridStartX, gridStartY)
                    val pos = Pair(row, col)
                    gridBalls[pos] = Ball(x, y, type, ballRadius)
                    originalBalls.add(pos) // Track as original ball
                }
            }
        }

        val emptyBall = BallType(0, android.graphics.Color.GRAY, "Empty", "")
        val originalCount = originalBalls.size
        gameState = GameState(
            level = level,
            movingBalls = movingBalls,
            gridBalls = gridBalls,
            emptyPositions = emptyPositions,
            poppingBalls = poppingBalls,
            fallingBalls = fallingBalls,
            currentBall = emptyBall,
            predictedLetter = "",
            canShoot = false,
            isGameOver = false,
            isGameWon = gridBalls.isEmpty(),
            score = 50 * originalCount,
            originalBallsCount = originalCount
        )
        onGameStateChanged?.invoke(gameState!!)
    }

    fun getCurrentGameState(): GameState? = gameState

    fun updateScore(newScore: Int) {
        val state = gameState ?: return
        gameState = state.copy(score = newScore)
        onGameStateChanged?.invoke(gameState!!)
    }

    fun onGesturePredicted(predictedLetter: String) {
        val state = gameState ?: return
        // Normalize prediction to a single A-Z letter
        val raw = predictedLetter.trim().uppercase()
        val trimmed = raw.firstOrNull { it in 'A'..'Z' }?.toString() ?: ""
        // Prefer configured ballTypes list from level JSON to determine availability
        val idsAvailable = if (state.level.ballTypes.isNotEmpty()) state.level.ballTypes.toSet() else state.level.layout.flatMap { row ->
            row.split("-").mapNotNull { it.toIntOrNull() }.filter { it > 0 }
        }.toSet()
        val matchingId = idsAvailable.firstOrNull { id ->
            state.level.getBallLetter(id).uppercase() == trimmed
        }
        // Debug mapping visibility
        try {
        android.util.Log.d(
                "BubbleShooter",
            "Predicted=" + raw + " | Clean=" + trimmed + " | AvailableLetters=" + idsAvailable.joinToString { id -> "$id:" + state.level.getBallLetter(id) }
            )
        } catch (_: Exception) {}
        val canShoot = matchingId != null
        val ballType = if (canShoot) {
            BallType.getBallTypeWithLetter(matchingId!!, trimmed)
        } else {
            BallType(0, android.graphics.Color.GRAY, "Unavailable", trimmed)
        }
        gameState = state.copy(
            predictedLetter = trimmed,
            currentBall = ballType,
            canShoot = canShoot
        )
        onGameStateChanged?.invoke(gameState!!)
    }

    fun shootBall(startX: Float, startY: Float, targetX: Float, targetY: Float) {
        val state = gameState ?: return
        if (!state.canShoot) return
        val dx = targetX - startX
        val dy = targetY - startY
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val vx = (dx / dist) * ballSpeed
        val vy = (dy / dist) * ballSpeed
        val shot = Ball(startX, startY, state.currentBall, ballRadius, isMoving = true, velocityX = vx, velocityY = vy)
        movingBalls.add(shot)
        val empty = BallType(0, android.graphics.Color.GRAY, "Empty", "")
        gameState = state.copy(currentBall = empty, predictedLetter = "", canShoot = false, movingBalls = movingBalls, gridBalls = gridBalls)
        onGameStateChanged?.invoke(gameState!!)
    }

    fun updateGame(deltaTime: Float, screenWidth: Float, screenHeight: Float) {
        val s = gameState ?: return
        if (s.isGameOver || s.isGameWon) return
        updateMovingBalls(deltaTime, screenWidth, screenHeight)
        checkCollisions()
        updateFallingBalls(deltaTime, screenHeight)
        checkWinCondition()
        gameState = s.copy(
            movingBalls = movingBalls,
            gridBalls = gridBalls,
            emptyPositions = emptyPositions,
            poppingBalls = poppingBalls,
            fallingBalls = fallingBalls
        )
        onGameStateChanged?.invoke(gameState!!)
    }

    private fun updateFallingBalls(deltaTime: Float, screenHeight: Float) {
        if (fallingBalls.isEmpty()) return
        val gravity = 2000f
        val it = fallingBalls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val newY = b.centerY + b.velocityY * deltaTime + 0.5f * gravity * deltaTime * deltaTime
            val newVy = b.velocityY + gravity * deltaTime
            if (newY + ballRadius >= screenHeight - ballRadius) {
                it.remove()
                continue
            }
            val updated = b.copy(centerY = newY, velocityY = newVy)
            val idx = fallingBalls.indexOf(b)
            if (idx >= 0) fallingBalls[idx] = updated
        }
    }

    private fun updateMovingBalls(dt: Float, screenWidth: Float, screenHeight: Float) {
        val it = movingBalls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            var nx = b.centerX + b.velocityX * dt
            var ny = b.centerY + b.velocityY * dt
            var vx = b.velocityX
            var vy = b.velocityY

            // Open walls by additional 1/2 ball
            val wallLeft = -ballRadius * 0.5f
            val wallRight = screenWidth + ballRadius * 0.5f

            val speed = sqrt(vx * vx + vy * vy)
            if (speed > 500f) {
                if (nx - ballRadius <= wallLeft + ballRadius) {
                    nx = wallLeft + ballRadius * 2f
                    vx = -vx * 0.85f
                } else if (nx + ballRadius >= wallRight - ballRadius) {
                    nx = wallRight - ballRadius * 2f
                    vx = -vx * 0.85f
                }
            }

            val topBoundaryY = gridStartY - ballRadius
            if (ny - ballRadius <= topBoundaryY) {
                val pos = findClosestGridPosition(b, screenWidth)
                if (pos != null) {
                    snapBallToGrid(b, pos)
                }
                it.remove()
                continue
            }

            if (ny + ballRadius >= screenHeight - ballRadius) {
                ny = screenHeight - ballRadius * 2f
                vy = -vy * 0.6f
                vx *= 0.8f
                if (abs(vx) < 50f && abs(vy) < 50f) {
                    it.remove(); continue
                }
            }

            val updated = b.copy(centerX = nx, centerY = ny, velocityX = vx, velocityY = vy)
            val idx = movingBalls.indexOf(b)
            if (idx >= 0) movingBalls[idx] = updated
        }
    }

    private fun checkCollisions() {
        val it = movingBalls.iterator()
        val threshold = ballRadius * 1.8f
        while (it.hasNext()) {
            val mb = it.next()
            val hit = gridBalls.values.minByOrNull { gb -> mb.distanceTo(gb) }
            if (hit != null && mb.distanceTo(hit) < threshold) {
                val best = findBestSnapNeighbor(mb, hit)
                if (best != null) {
                    snapBallToGrid(mb, best)
                    it.remove()
                }
            }
        }
    }

    private fun findBestSnapNeighbor(movingBall: Ball, hitBall: Ball): Pair<Int, Int>? {
        val hitPos = gridBalls.entries.find { it.value == hitBall }?.key ?: return null
        val neighbors = GridPosition.getNeighbors(hitPos.first, hitPos.second)
        var best: Pair<Int, Int>? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (n in neighbors) {
            if (!gridBalls.containsKey(n) && isGridPositionWithinWalls(n)) {
                val (nx, ny) = GridPosition.toScreenPosition(n.first, n.second, ballRadius, gridStartX, gridStartY)
                val dx = nx - hitBall.centerX
                val dy = ny - hitBall.centerY
                val nd = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val ndx = dx / nd
                val ndy = dy / nd
                val cdx = movingBall.centerX - hitBall.centerX
                val cdy = movingBall.centerY - hitBall.centerY
                val cd = sqrt(cdx * cdx + cdy * cdy).coerceAtLeast(1f)
                val cx = cdx / cd
                val cy = cdy / cd
                val dot = cx * ndx + cy * ndy
                val distScore = 1f / (1f + nd / ballRadius)
                val score = dot * 0.7f + distScore * 0.3f
                if (score > bestScore) { bestScore = score; best = n }
            }
        }
        return best
    }

    private fun snapBallToGrid(ball: Ball, position: Pair<Int, Int>) {
        val (x, y) = GridPosition.toScreenPosition(position.first, position.second, ballRadius, gridStartX, gridStartY)
        val snapped = ball.copy(centerX = x, centerY = y, isMoving = false, velocityX = 0f, velocityY = 0f)
        gridBalls[position] = snapped
        emptyPositions.remove(position)
        newlyAttachedBalls.add(position)
        // Lose condition: any ball touches the red line (row 7)
        if (position.first >= 7) {
            val s = gameState ?: return
            gameState = s.copy(isGameOver = true, isGameWon = false, canShoot = false)
            onGameStateChanged?.invoke(gameState!!)
            return
        }
        checkMatching()
    }

    private fun checkMatching() {
        val state = gameState ?: return
        val allBalls = mutableMapOf<Pair<Int, Int>, Ball>().apply { putAll(gridBalls) }
        val toPop = mutableSetOf<Pair<Int, Int>>()
        for (pos in newlyAttachedBalls) {
            val ball = gridBalls[pos] ?: continue
            val group = findMatching(pos, ball.ballType, allBalls)
            if (group.size >= 3 && pos in group) toPop.addAll(group)
        }
        newlyAttachedBalls.clear()
        if (toPop.isEmpty()) return
        
        // Remove popped balls
        val popped = mutableListOf<Ball>()
        for (p in toPop) {
            val gb = gridBalls.remove(p)
            if (gb != null) {
                poppingBalls[p] = gb
                popped.add(gb)
                // Remove from original balls if it was one
                if (p in originalBalls) {
                    originalBalls.remove(p)
                }
            }
        }
        
        // after pop, drop disconnected
        checkFallingBalls()
        onGameStateChanged?.invoke(state)
        
        // Check win condition after popping
        checkWinCondition()
    }

    private fun findMatching(start: Pair<Int, Int>, type: BallType, all: Map<Pair<Int, Int>, Ball>): Set<Pair<Int, Int>> {
        val visited = mutableSetOf<Pair<Int, Int>>()
        fun dfs(p: Pair<Int, Int>) {
            if (p in visited) return
            val b = all[p] ?: return
            if (b.ballType != type) return
            visited.add(p)
            for (n in GridPosition.getNeighbors(p.first, p.second)) if (all.containsKey(n)) dfs(n)
        }
        dfs(start)
        return visited
    }

    private fun checkFallingBalls() {
        val connected = mutableSetOf<Pair<Int, Int>>()
        for (c in 0 until (gameState?.level?.getGridWidth() ?: 0)) {
            val top = Pair(0, c)
            if (gridBalls.containsKey(top)) visitConnected(top, connected)
        }
        val toFall = gridBalls.keys.filter { it !in connected }
        for (p in toFall) {
            val ball = gridBalls.remove(p)
            if (ball != null) {
                fallingBalls.add(ball.copy(isFalling = true))
                // Remove from original balls if it falls (doesn't count for score)
                originalBalls.remove(p)
            }
        }
        // Check win condition after balls fall (in case all originals fell)
        checkWinCondition()
    }

    private fun checkWinCondition() {
        val s = gameState ?: return
        // Win if all original balls are popped (not just grid is empty, but all originals are gone)
        if (originalBalls.isEmpty() && !s.isGameWon) {
            gameState = s.copy(isGameWon = true, canShoot = false)
            onGameStateChanged?.invoke(gameState!!)
        }
    }

    private fun visitConnected(pos: Pair<Int, Int>, connected: MutableSet<Pair<Int, Int>>) {
        if (!gridBalls.containsKey(pos) || pos in connected) return
        connected.add(pos)
        for (n in GridPosition.getNeighbors(pos.first, pos.second)) visitConnected(n, connected)
    }

    private fun isGridPositionWithinWalls(position: Pair<Int, Int>, screenWidth: Float = 1000f): Boolean {
        val (x, y) = GridPosition.toScreenPosition(position.first, position.second, ballRadius, gridStartX, gridStartY)
        // Open walls by additional 1/2 ball
        val wallLeft = -ballRadius * 0.25f
        val wallRight = screenWidth + ballRadius * 0.25f
        val left = x - ballRadius
        val right = x + ballRadius
        if (left <= wallLeft - ballRadius) return false
        if (right >= wallRight + ballRadius) return false
        val topBoundaryY = gridStartY - ballRadius
        if (y - ballRadius < topBoundaryY) return false
        return true
    }

    private fun findClosestGridPosition(ball: Ball, screenWidth: Float): Pair<Int, Int>? {
        val level = gameState?.level ?: return null
        var best: Pair<Int, Int>? = null
        var bestDist = Float.MAX_VALUE
        for (r in 0 until level.getGridHeight()) {
            for (c in 0 until level.getGridWidth()) {
                val p = Pair(r, c)
                if (gridBalls.containsKey(p)) continue
                if (!isGridPositionWithinWalls(p, screenWidth)) continue
                val (x, y) = GridPosition.toScreenPosition(r, c, ballRadius, gridStartX, gridStartY)
                val d = sqrt((ball.centerX - x).pow(2) + (ball.centerY - y).pow(2))
                if (d < bestDist) { bestDist = d; best = p }
            }
        }
        return best
    }
}


