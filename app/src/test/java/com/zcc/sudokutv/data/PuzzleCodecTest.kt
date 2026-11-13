package com.zcc.sudokutv.data

import com.zcc.sudokutv.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class PuzzleCodecTest {
    private val digits = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    private fun encode(digits: String, ratingTenths: Int): ByteArray {
        val bytes = ByteArray(PuzzleCodec.RECORD_BYTES)
        digits.forEachIndexed { index, character ->
            val value = character - '0'
            val byteIndex = index / 2
            val current = bytes[byteIndex].toInt() and 0xff
            bytes[byteIndex] = if (index % 2 == 0) {
                ((value shl 4) or (current and 0x0f)).toByte()
            } else {
                ((current and 0xf0) or value).toByte()
            }
        }
        bytes[41] = ratingTenths.toByte()
        return bytes
    }

    @Test
    fun decodesNibblesAndRating() {
        val puzzle = PuzzleCodec.decodeRecord(encode(digits, 47), 12, Difficulty.HARD)
        assertEquals(12, puzzle.id)
        assertEquals(Difficulty.HARD, puzzle.difficulty)
        assertEquals(47, puzzle.ratingTenths)
        assertEquals("4.7", puzzle.rating)
        assertEquals(digits, puzzle.givens.joinToString(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongRecordSize() {
        PuzzleCodec.decodeRecord(ByteArray(41), 1, Difficulty.EASY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCellValue() {
        val bytes = encode(digits, 12)
        bytes[0] = 0x0f.toByte()
        PuzzleCodec.decodeRecord(bytes, 1, Difficulty.EASY)
    }
}
