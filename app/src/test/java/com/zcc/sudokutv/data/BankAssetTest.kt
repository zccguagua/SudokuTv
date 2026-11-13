package com.zcc.sudokutv.data

import com.zcc.sudokutv.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Reads the generated bank files straight from the module assets directory. */
class BankAssetTest {
    private val bankDir = File("src/main/assets/bank")

    private fun file(difficulty: Difficulty) = File(bankDir, "${difficulty.fileName}.bin")

    @Test
    fun countsMatchTheSourceBank() {
        assertTrue("缺少题库资产：$bankDir", bankDir.isDirectory)
        val expected = mapOf(
            Difficulty.EASY to 100_000,
            Difficulty.MEDIUM to 352_643,
            Difficulty.HARD to 321_592,
            Difficulty.EXPERT to 119_681,
        )
        expected.forEach { (difficulty, count) ->
            val records = file(difficulty).length() / PuzzleCodec.RECORD_BYTES
            assertEquals("${difficulty.label} 题数", count.toLong(), records)
            assertEquals(file(difficulty).length(), records * PuzzleCodec.RECORD_BYTES)
        }
    }

    @Test
    fun diabolical119681MatchesTheSourceLine() {
        assertTrue("缺少题库资产：$bankDir", bankDir.isDirectory)
        val bytes = file(Difficulty.EXPERT).readBytes()
        val offset = (119_681 - 1) * PuzzleCodec.RECORD_BYTES
        val puzzle = PuzzleCodec.decodeRecord(
            bytes.copyOfRange(offset, offset + PuzzleCodec.RECORD_BYTES),
            119_681,
            Difficulty.EXPERT,
        )
        assertEquals(
            "040000000807000010150090000008910600900706002001025400000050039060000507000000060",
            puzzle.givens.joinToString(""),
        )
        assertEquals(69, puzzle.ratingTenths)
        assertEquals("6.9", puzzle.rating)
    }

    @Test
    fun sampledRecordsOnlyContainLegalCells() {
        assertTrue("缺少题库资产：$bankDir", bankDir.isDirectory)
        val random = Random(20261113)
        Difficulty.entries.forEach { difficulty ->
            val bytes = file(difficulty).readBytes()
            val count = bytes.size / PuzzleCodec.RECORD_BYTES
            repeat(200) {
                val id = random.nextInt(1, count + 1)
                val offset = (id - 1) * PuzzleCodec.RECORD_BYTES
                val puzzle = PuzzleCodec.decodeRecord(
                    bytes.copyOfRange(offset, offset + PuzzleCodec.RECORD_BYTES),
                    id,
                    difficulty,
                )
                assertTrue("$difficulty #$id 题面格数", puzzle.givens.count { it != 0 } in 17..64)
                assertTrue("$difficulty #$id 评分", puzzle.ratingTenths in 0..255)
            }
        }
    }
}
