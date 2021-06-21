package dev.aspid812.virtualfs

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class AbstractVirtualFileEntity : VirtualFile {
	companion object {
		private const val MIN_CONTENT_CAPACITY = 16

		private val NO_CONTENT = ByteArray(0)
	}

	private val lock = ReentrantReadWriteLock()

	private var content = NO_CONTENT
	private var contentSize = 0

	private var openCounter = 0

	private var isModified = false

	protected abstract fun load(): ByteArray
	protected abstract fun store(content: ByteArray, offset: Int, length: Int)

	fun isOpen(): Boolean {
		return openCounter != 0
	}

	fun open(): VirtualFile {
		lock.write {
			val count = openCounter
			if (count == 0) {
				content = load()
				contentSize = content.size
				isModified = false
			}
			openCounter = count + 1
		}
		return this
	}

	fun close() {
		lock.write {
			val count = openCounter - 1
			if (isModified && count == 0) {
				store(content, 0, contentSize)
				isModified = false
			}
		}
	}

	private fun ensureCapacity(demand: Int) {
		var capacity = maxOf(content.size, MIN_CONTENT_CAPACITY)
		while (capacity < demand) {
			capacity *= 2
		}
		if (capacity > content.size) {
			content = content.copyOf(capacity)
		}
	}

	override fun read(dst: ByteBuffer, position: Int): Int {
		lock.read {
			if (position >= contentSize) {
				return -1
			}
			val bytesRead = minOf(contentSize - position, dst.remaining())
			dst.put(content, position, bytesRead)
			return bytesRead
		}
	}

	override fun write(src: ByteBuffer, position: Int): Int {
		lock.write {
			isModified = true
			val bytesWrite = src.remaining()
			if (position + bytesWrite > contentSize) {
				ensureCapacity(position + bytesWrite)
				src.get(content, position, bytesWrite)
				contentSize = position + bytesWrite
			}
			else {
				src.get(content, position, bytesWrite)
			}
			return bytesWrite
		}
	}
}