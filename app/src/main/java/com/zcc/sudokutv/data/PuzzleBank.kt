package com.zcc.sudokutv.data

import android.content.Context
import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.Puzzle
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Random access to the generated bank assets. Each record is read individually, so a 15 MB
 * difficulty file never has to be loaded into memory.
 */
class PuzzleBank(context: Context) {
    private val assets = context.assets
    private val counts = HashMap<Difficulty, Int>()

    fun count(difficulty: Difficulty): Int = counts.getOrPut(difficulty) {
        assets.openFd(assetPath(difficulty)).use { descriptor ->
            (descriptor.length / PuzzleCodec.RECORD_BYTES).toInt()
        }
    }

    fun read(difficulty: Difficulty, id: Int): Puzzle? {
        if (id < 1 || id > count(difficulty)) return null
        assets.openFd(assetPath(difficulty)).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val buffer = ByteBuffer.allocate(PuzzleCodec.RECORD_BYTES)
                input.channel.position(descriptor.startOffset + (id - 1).toLong() * PuzzleCodec.RECORD_BYTES)
                while (buffer.hasRemaining()) {
                    if (input.channel.read(buffer) < 0) return null
                }
                return PuzzleCodec.decodeRecord(buffer.array(), id, difficulty)
            }
        }
    }

    fun randomId(difficulty: Difficulty, exclude: Int? = null): Int {
        val total = count(difficulty)
        return if (total <= 1 || exclude == null) {
            Random.nextInt(1, total + 1)
        } else {
            var id = Random.nextInt(1, total + 1)
            if (id == exclude) id = if (id == total) id - 1 else id + 1
            id
        }
    }

    private fun assetPath(difficulty: Difficulty) = "bank/${difficulty.fileName}.bin"
}
