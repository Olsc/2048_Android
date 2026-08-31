package com.olsc.a2048

import com.olsc.a2048.Game2048.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Game2048Test {

    private fun Game2048.setRow(r: Int, vararg values: Int) {
        for (c in values.indices) grid[r][c] = values[c]
    }

    private fun Game2048.setCol(c: Int, vararg values: Int) {
        for (r in values.indices) grid[r][c] = values[r]
    }

    @Test
    fun `slide left merges and compacts`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 4, 0)
        val result = g.move(Direction.LEFT)
        val anims = result!!.anims
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(4, g.score)
        assertTrue(anims.isNotEmpty())
    }

    @Test
    fun `tiles merge only once per move`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 2, 2)
        g.move(Direction.LEFT)
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(8, g.score)
    }

    @Test
    fun `no move when nothing can move`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 4, 2, 4)
        g.setRow(1, 4, 2, 4, 2)
        g.setRow(2, 2, 4, 2, 4)
        g.setRow(3, 4, 2, 4, 2)
        val result = g.move(Direction.LEFT)
        assertTrue(result == null)
        assertTrue(g.gameOver)
    }

    @Test
    fun `right slide moves toward right edge`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 2, 4)
        g.move(Direction.RIGHT)
        assertEquals(0, g.grid[0][0])
        assertEquals(0, g.grid[0][1])
        assertEquals(4, g.grid[0][2])
        assertEquals(4, g.grid[0][3])
        assertEquals(4, g.score)
    }

    @Test
    fun `up slide compacts column`() {
        val g = Game2048(spawnNewTiles = false)
        g.setCol(0, 2, 0, 2, 4)
        g.move(Direction.UP)
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[1][0])
        assertEquals(0, g.grid[2][0])
        assertEquals(0, g.grid[3][0])
        assertEquals(4, g.score)
    }

    @Test
    fun `down slide compacts column`() {
        val g = Game2048(spawnNewTiles = false)
        g.setCol(0, 2, 0, 2, 4)
        g.move(Direction.DOWN)
        assertEquals(0, g.grid[0][0])
        assertEquals(0, g.grid[1][0])
        assertEquals(4, g.grid[2][0])
        assertEquals(4, g.grid[3][0])
        assertEquals(4, g.score)
    }

    @Test
    fun `gap between equal tiles still merges`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 0, 2)
        g.move(Direction.LEFT)
        assertEquals(4, g.grid[0][0])
        assertEquals(0, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(4, g.score)
    }

    @Test
    fun `reset spawns two tiles`() {
        val g = Game2048(spawnNewTiles = false)
        g.reset()
        var count = 0
        for (r in 0 until 4) for (c in 0 until 4) if (g.grid[r][c] != 0) count++
        assertEquals(2, count)
        assertFalse(g.gameOver)
    }

    @Test
    fun `restoreState rebuilds grid score and best`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 2, 4)
        g.move(Direction.RIGHT)
        val g2 = Game2048(spawnNewTiles = false)
        g2.restoreState(g.flattenedGrid(), g.score, 100)
        assertEquals(4, g2.grid[0][2])
        assertEquals(4, g2.grid[0][3])
        assertEquals(4, g2.score)
        assertEquals(100, g2.best)
        assertFalse(g2.gameOver)
    }

    @Test
    fun `restoreState detects dead grid`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 4, 2, 4)
        g.setRow(1, 4, 2, 4, 2)
        g.setRow(2, 2, 4, 2, 4)
        g.setRow(3, 4, 2, 4, 2)
        val g2 = Game2048(spawnNewTiles = false)
        g2.restoreState(g.flattenedGrid(), 0, 0)
        assertTrue(g2.gameOver)
    }

    @Test
    fun `merge anims carry correct coordinates`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 0, 0)
        val result = g.move(Direction.LEFT)
        val anims = result!!.anims
        val merge = anims.firstOrNull { it.merge }
        assertTrue(merge != null)
        assertEquals(0, merge!!.fromRow)
        assertEquals(0, merge.fromCol)
        assertEquals(0, merge.toRow)
        assertEquals(0, merge.toCol)
        assertEquals(4, merge.value)
        val consumed = anims.firstOrNull { it.consumed }
        assertTrue(consumed != null)
        assertEquals(0, consumed!!.toRow)
        assertEquals(0, consumed.toCol)
    }

    @Test
    fun `smart spawn protects original corner position when large tile shifts`() {
        val g = Game2048(spawnNewTiles = false)
        // (0,0) 有大数 1024
        g.setRow(0, 1024, 512, 256, 128)
        g.setRow(1, 64, 32, 16, 8)
        g.setRow(2, 4, 2, 0, 0)
        g.setRow(3, 2, 0, 0, 0)

        val oldGrid = Array(4) { r -> g.grid[r].clone() }
        // 误触向右滑动，使 1024 离开 (0,0) 挪到了 (0,1)
        g.move(Direction.RIGHT)

        // 验证 (0,0) 变空
        assertEquals(0, g.grid[0][0])
        assertEquals(1024, g.grid[0][1])

        // 调用智能生成
        val spawned = g.spawnSmartTile(oldGrid, Direction.RIGHT, isForcedMove = false)

        // 验证生成的位置绝对不能是原本大数所在且现在空出的 (0,0)
        assertTrue(spawned != null)
        assertTrue(spawned != (0 to 0))
        // 验证 (0,0) 仍然为 0，以便玩家滑动切回
        assertEquals(0, g.grid[0][0])
    }

    @Test
    fun `forced move detects single direction and spawns away from large numbers`() {
        val g = Game2048(spawnNewTiles = false)
        // 构造仅能向下滑动的局面
        g.setRow(0, 16, 8, 4, 2)
        g.setRow(1, 0, 0, 0, 0)
        g.setRow(2, 0, 0, 0, 0)
        g.setRow(3, 0, 0, 0, 0)

        val oldGrid = Array(4) { r -> g.grid[r].clone() }
        val movesCount = g.countValidMoves(oldGrid)
        assertEquals(1, movesCount) // 仅 DOWN 为有效移动

        g.move(Direction.DOWN)

        val spawned = g.spawnSmartTile(oldGrid, Direction.DOWN, isForcedMove = true)
        assertTrue(spawned != null)

        // 大数被推到了第 3 行 (r=3)，最远的位置应该在第 0 行 (r=0)
        assertEquals(0, spawned!!.first)
    }
}
