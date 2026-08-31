package com.olsc.a2048

import kotlin.random.Random

/**
 * 2048 游戏引擎：4x4 网格、滑动合并、得分、胜负判定。
 * 纯 Kotlin 实现，不依赖 Android，便于单元测试。
 */
class Game2048(private val spawnNewTiles: Boolean = true) {

    companion object {
        const val SIZE = 4
        const val WIN_VALUE = 2048
    }

    /** 移动方向。滑动合并时朝目标方向压缩。 */
    enum class Direction(val dr: Int, val dc: Int) {
        UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1)
    }

    /**
     * 一次滑动产生的一条瓦片动画指令。
     * @param fromRow/fromCol 瓦片移动前的格子
     * @param toRow/toCol 瓦片移动后的格子
     * @param value 瓦片数值
     * @param merge 该瓦片是合并后的幸存者（需要弹跳强调）
     * @param consumed 该瓦片被合并吃掉（移动到目标后消失）
     */
    data class TileAnim(
        val fromRow: Int, val fromCol: Int,
        val toRow: Int, val toCol: Int,
        val value: Int,
        val merge: Boolean = false,
        val consumed: Boolean = false,
    )

    private val random = Random.Default

    /** 当前棋盘，[grid][r][c]，0 表示空。 */
    val grid = Array(SIZE) { IntArray(SIZE) }

    var score = 0
        private set

    var best = 0
        private set

    var gameOver = false
        private set

    var won = false
        private set

    fun reset() {
        for (r in 0 until SIZE) for (c in 0 until SIZE) grid[r][c] = 0
        score = 0
        gameOver = false
        won = false
        addRandomTile()
        addRandomTile()
    }

    /** 在随机空位生成 2（90%）或 4（10%）。返回生成位置，无空位返回 null。 */
    fun addRandomTile(): Pair<Int, Int>? {
        val empty = ArrayList<Pair<Int, Int>>(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return null
        val (r, c) = empty[random.nextInt(empty.size)]
        grid[r][c] = if (random.nextInt(10) == 0) 4 else 2
        return r to c
    }

    fun setBest(value: Int) {
        best = value
    }

    /** 从存档恢复棋盘（用于 Activity 重建）。 */
    fun restoreState(gridValues: IntArray, savedScore: Int, savedBest: Int) {
        require(gridValues.size == SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            grid[r][c] = gridValues[r * SIZE + c]
        }
        score = savedScore
        best = savedBest
        gameOver = !hasAnyMove()
        won = grid.any { row -> row.any { it >= WIN_VALUE } }
    }

    /** 展平的棋盘值，用于存档。 */
    fun flattenedGrid(): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) out[r * SIZE + c] = grid[r][c]
        return out
    }

    /** 一次滑动后的完整结果，供 UI 驱动动画。 */
    data class MoveResult(
        val anims: List<TileAnim>,
        val newTileRow: Int,
        val newTileCol: Int,
        val score: Int,
        val gameOver: Boolean,
        val won: Boolean,
    )

    /**
     * 朝 [direction] 滑动并合并。返回动画指令；若没有任何移动则返回 null。
     */
    fun move(direction: Direction): MoveResult? {
        val oldGrid = Array(SIZE) { r -> grid[r].clone() }
        val validMovesBefore = countValidMoves(oldGrid)
        val isForcedMove = (validMovesBefore == 1)

        val anims = ArrayList<TileAnim>()
        var anyMoved = false

        // 按方向把网格投影成若干条"从左到右"的线，统一做左滑合并，再映射回坐标。
        for (i in 0 until SIZE) {
            val cells = ArrayList<Pair<Int, Int>>(SIZE) // 线上的格子坐标
            val values = IntArray(SIZE)
            for (j in 0 until SIZE) {
                val (r, c) = when (direction) {
                    Direction.LEFT -> i to j
                    Direction.RIGHT -> i to (SIZE - 1 - j)
                    Direction.UP -> j to i
                    Direction.DOWN -> (SIZE - 1 - j) to i
                }
                cells.add(r to c)
                values[j] = grid[r][c]
            }

            val result = slideLine(values)
            anyMoved = anyMoved || result.moved

            for (j in 0 until SIZE) {
                val (r, c) = cells[j]
                grid[r][c] = result.line[j]
            }

            // 输出动画指令：幸存者 + 被吃掉的瓦片
            for (k in result.survivors.indices) {
                val s = result.survivors[k]
                val (toR, toC) = cells[k]
                if (s.sourceIndex == k && !s.merged) continue // 没动也没合并

                if (s.merged) {
                    // 幸存者：来自 s.sourceIndex，弹跳
                    val (fromR, fromC) = cells[s.sourceIndex]
                    anims.add(
                        TileAnim(fromR, fromC, toR, toC, s.value, merge = true)
                    )
                    // 被吃掉的瓦片：来自 s.mergedFrom
                    val (cR, cC) = cells[s.mergedFrom]
                    anims.add(
                        TileAnim(cR, cC, toR, toC, s.value / 2, consumed = true)
                    )
                } else {
                    val (fromR, fromC) = cells[s.sourceIndex]
                    anims.add(
                        TileAnim(fromR, fromC, toR, toC, s.value)
                    )
                }
            }
        }

        if (anyMoved) {
            val newTile = if (spawnNewTiles) spawnSmartTile(oldGrid, direction, isForcedMove) else null
            if (score > best) best = score
            gameOver = !hasAnyMove()
            return MoveResult(
                anims,
                newTile?.first ?: -1,
                newTile?.second ?: -1,
                score,
                gameOver,
                won,
            )
        }
        // 无移动时也刷新死局判定：棋盘可能此前已不可移动
        gameOver = !hasAnyMove()
        return null
    }

    /**
     * 在滑动后智能选择新瓦片的生成位置，提升游玩体验并减少“手误”惩罚。
     *
     * 策略说明：
     * 1. 保护原大数聚集点（角落/高值格子）：
     *    分析 oldGrid 中大数（如 maxTile >= 16）的位置。若滑动导致大数离开原本所在的位置/角落（使得原位置在 newGrid 中空出），
     *    将原位置标记为保护区域，新瓦片尽可能不填入原位置，确保玩家滑回时大数能原路归位。
     * 2. 距离大数的加权距离（避开大数）：
     *    对 newGrid 中的所有瓦片根据其数值赋予权重，计算每个候选空格到大数聚集点的加权距离。
     * 3. 强迫移动 vs 常规移动：
     *    - 若为强迫移动 (isForcedMove = true，即移动前仅有 1 个可移动方向)，直接选择距离大数最远的空格，最大程度保留操作空间。
     *    - 若为常规移动，加权轮盘赌偏向远离大数的位置，兼顾随机性与舒适度。
     */
    fun spawnSmartTile(
        oldGrid: Array<IntArray>,
        direction: Direction,
        isForcedMove: Boolean,
    ): Pair<Int, Int>? {
        val empty = ArrayList<Pair<Int, Int>>(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return null
        if (empty.size == 1) {
            val (r, c) = empty[0]
            grid[r][c] = if (random.nextInt(10) == 0) 4 else 2
            return r to c
        }

        // 1. 找出 oldGrid 中的最大瓦片值 oldMax
        var oldMax = 0
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (oldGrid[r][c] > oldMax) oldMax = oldGrid[r][c]
        }

        // 收集受保护的位置 (protectedSpots)：原大数所在且现在空出来的格子
        val protectedSpots = HashSet<Pair<Int, Int>>()
        if (oldMax >= 16) {
            // 大数阈值：最大值以及次大值 (>= oldMax / 2)
            val threshold = oldMax / 2
            for (r in 0 until SIZE) for (c in 0 until SIZE) {
                if (oldGrid[r][c] >= threshold && grid[r][c] == 0) {
                    protectedSpots.add(r to c)
                }
            }
        }

        // 过滤受保护格子（若有其他空格可用，则排除 protectedSpots）
        val candidates = empty.filter { it !in protectedSpots }
        val pool = if (candidates.isNotEmpty()) candidates else empty

        if (pool.size == 1) {
            val (r, c) = pool[0]
            grid[r][c] = if (random.nextInt(10) == 0) 4 else 2
            return r to c
        }

        // 2. 计算 pool 中每个 candidate 到 newGrid 中大数聚集点的加权距离
        var totalWeight = 0.0
        val weights = Array(SIZE) { DoubleArray(SIZE) }
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val v = grid[r][c]
            if (v >= 4) {
                val w = v.toDouble() * v.toDouble()
                weights[r][c] = w
                totalWeight += w
            }
        }

        val scoredCandidates = pool.map { (r, c) ->
            val dist = if (totalWeight > 0) {
                var dSum = 0.0
                for (br in 0 until SIZE) for (bc in 0 until SIZE) {
                    if (weights[br][bc] > 0) {
                        val manhattan = (kotlin.math.abs(r - br) + kotlin.math.abs(c - bc)).toDouble()
                        dSum += weights[br][bc] * manhattan
                    }
                }
                dSum / totalWeight
            } else {
                1.0
            }
            (r to c) to dist
        }

        // 3. 根据是否强迫移动选择生成坐标
        val selected: Pair<Int, Int> = if (isForcedMove) {
            // 强迫移动：选择距离最远的空格
            val maxDist = scoredCandidates.maxOf { it.second }
            val bestSpots = scoredCandidates.filter { kotlin.math.abs(it.second - maxDist) < 1e-4 }
            bestSpots[random.nextInt(bestSpots.size)].first
        } else {
            // 常规移动：按 dist^2 进行加权概率选择，强烈偏向远端但保留适度随机性
            val weightedList = scoredCandidates.map { it.first to (it.second * it.second) }
            val sumW = weightedList.sumOf { it.second }
            if (sumW > 0) {
                var rnd = random.nextDouble() * sumW
                var chosen = weightedList.last().first
                for ((spot, w) in weightedList) {
                    rnd -= w
                    if (rnd <= 0) {
                        chosen = spot
                        break
                    }
                }
                chosen
            } else {
                pool[random.nextInt(pool.size)]
            }
        }

        grid[selected.first][selected.second] = if (random.nextInt(10) == 0) 4 else 2
        return selected
    }

    /** 判断指定网格在朝 targetDirection 方向滑动时是否会发生任何移动或合并。 */
    fun canMoveInDirection(g: Array<IntArray>, direction: Direction): Boolean {
        for (i in 0 until SIZE) {
            val values = IntArray(SIZE)
            for (j in 0 until SIZE) {
                val (r, c) = when (direction) {
                    Direction.LEFT -> i to j
                    Direction.RIGHT -> i to (SIZE - 1 - j)
                    Direction.UP -> j to i
                    Direction.DOWN -> (SIZE - 1 - j) to i
                }
                values[j] = g[r][c]
            }

            var read = 0
            var write = 0
            while (read < SIZE) {
                if (values[read] == 0) { read++; continue }
                val v = values[read]
                var next = read + 1
                while (next < SIZE && values[next] == 0) next++
                if (next < SIZE && values[next] == v) {
                    return true // 发生合并
                } else {
                    if (read != write) return true // 发生位移
                    write++
                    read++
                }
            }
        }
        return false
    }

    /** 统计指定网格当前可移动的方向数量。 */
    fun countValidMoves(g: Array<IntArray>): Int {
        var count = 0
        for (d in Direction.values()) {
            if (canMoveInDirection(g, d)) count++
        }
        return count
    }

    /** 判断是否还有任何可移动（空格或相邻相等）。 */
    fun hasAnyMove(): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (grid[r][c] == 0) return true
            if (r + 1 < SIZE && grid[r][c] == grid[r + 1][c]) return true
            if (c + 1 < SIZE && grid[r][c] == grid[r][c + 1]) return true
        }
        return false
    }

    /** 一行的左滑合并结果。 */
    private data class SlideResult(
        val line: IntArray,
        val survivors: List<Survivor>,
        val moved: Boolean,
    )

    private data class Survivor(val sourceIndex: Int, val mergedFrom: Int, val value: Int, val merged: Boolean)

    private fun slideLine(values: IntArray): SlideResult {
        val line = IntArray(SIZE)
        val survivors = ArrayList<Survivor>(SIZE)

        var write = 0
        var read = 0
        var moved = false

        while (read < SIZE) {
            if (values[read] == 0) { read++; continue }
            val v = values[read]

            // 与下一个非零相同则合并
            var next = read + 1
            while (next < SIZE && values[next] == 0) next++
            if (next < SIZE && values[next] == v) {
                val newVal = v * 2
                line[write] = newVal
                survivors.add(Survivor(read, next, newVal, merged = true))
                score += newVal
                if (newVal >= WIN_VALUE) won = true
                moved = true // 合并必然改变棋盘
                write++
                read = next + 1
            } else {
                line[write] = v
                survivors.add(Survivor(read, -1, v, merged = false))
                if (read != write) moved = true
                write++
                read++
            }
        }
        return SlideResult(line, survivors, moved)
    }
}
