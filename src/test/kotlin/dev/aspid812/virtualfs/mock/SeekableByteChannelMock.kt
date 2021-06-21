package dev.aspid812.virtualfs.mock

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

class SeekableByteChannelMock(
	dataSize: Int
) : SeekableByteChannel {
	val data = ByteArray(dataSize)
	var pos = 0

	override fun close() {}

	override fun isOpen(): Boolean {
		return true
	}

	override fun read(dst: ByteBuffer): Int {
		if (pos >= data.size) {
			return -1
		}
		val bytesRead = minOf(dst.remaining(), data.size - pos)
		dst.put(data, pos, bytesRead)
		pos += bytesRead
		return bytesRead
	}

	override fun write(src: ByteBuffer): Int {
		if (pos >= data.size) {
			return -1
		}
		val bytesWrite = minOf(src.remaining(), data.size - pos)
		src.get(data, pos, bytesWrite)
		pos += bytesWrite
		return bytesWrite
	}

	override fun position(): Long {
		return pos.toLong()
	}

	override fun position(newPosition: Long): SeekableByteChannel {
		pos = newPosition.toInt()
		return this
	}

	override fun size(): Long {
		return data.size.toLong()
	}

	override fun truncate(size: Long): SeekableByteChannel {
		TODO("Not yet implemented")
	}
}