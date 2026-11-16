package com.zcc.sudokutv.data

import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Reads the bank files straight from the module assets directory.
 *
 * The shipped bank is a truncated copy of the source bank: every difficulty keeps the first
 * [RETAINED_PER_DIFFICULTY] records, so puzzle numbers stay in `1..20000`.
 */
class BankAssetTest {
    private val bankDir = File("src/main/assets/bank")

    private fun file(difficulty: Difficulty) = File(bankDir, "${difficulty.fileName}.bin")

    private fun decode(difficulty: Difficulty, id: Int): Puzzle {
        val bytes = file(difficulty).readBytes()
        val offset = (id - 1) * PuzzleCodec.RECORD_BYTES
        return PuzzleCodec.decodeRecord(
            bytes.copyOfRange(offset, offset + PuzzleCodec.RECORD_BYTES),
            id,
            difficulty,
        )
    }

    @Test
    fun countsMatchTheRetainedBank() {
        assertTrue("缺少题库资产：$bankDir", bankDir.isDirectory)
        Difficulty.entries.forEach { difficulty ->
            assertEquals(
                "${difficulty.label} 字节数",
                RETAINED_PER_DIFFICULTY.toLong() * PuzzleCodec.RECORD_BYTES,
                file(difficulty).length(),
            )
        }
    }

    @Test
    fun diabolicalHighestRatedRecordIsIntact() {
        val puzzle = decode(Difficulty.EXPERT, 12_070)
        assertEquals(
            "000100000008090300170800000020000067061050930930000040000002056003040700000001000",
            puzzle.givens.joinToString(""),
        )
        assertEquals(92, puzzle.ratingTenths)
        assertEquals("9.2", puzzle.rating)
    }

    @Test
    fun diabolicalLastRecordIsIntact() {
        val puzzle = decode(Difficulty.EXPERT, RETAINED_PER_DIFFICULTY)
        assertEquals(
            "008000304001508000500090600080000700706050801003000040009040007000307400304000200",
            puzzle.givens.joinToString(""),
        )
        assertEquals("5.7", puzzle.rating)
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

    private companion object {
        const val RETAINED_PER_DIFFICULTY = 20_000
    }
}
