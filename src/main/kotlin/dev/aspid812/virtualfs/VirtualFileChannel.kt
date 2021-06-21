package dev.aspid812.virtualfs

import java.io.Closeable
import java.nio.channels.ClosedChannelException
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class VirtualFileChannel(
	private val file: VirtualFile,
	private val isReadOnly: Boolean
) : Closeable {
	private val lock = ReentrantLock()
	private var position = 0

	override fun close() {
		lock.lock()
		try {
			if (position != -1) {
				doClose()
			}
		}
		finally {
			position = -1
			lock.unlock()
		}
	}

	abstract fun doClose()

	fun read(dst: ByteBuffer, position: Int): Int {
		lock.withLock {
			if (this.position == -1) {
				throw ClosedChannelException()
			}
			return file.read(dst, position);
		}
	}

	fun read(dst: ByteBuffer): Int {
		lock.withLock {
			if (position == -1) {
				throw ClosedChannelException()
			}
			val bytesRead = file.read(dst, position)
			if (bytesRead > 0) {
				position += bytesRead
			}
			return bytesRead
		}
	}

	fun write(src: ByteBuffer, position: Int): Int {
		if (isReadOnly) {
			throw NonWritableChannelException()
		}
		lock.withLock {
			if (this.position == -1) {
				throw ClosedChannelException()
			}
			return file.write(src, position)
		}
	}

	fun write(src: ByteBuffer): Int {
		if (isReadOnly) {
			throw NonWritableChannelException()
		}
		lock.withLock {
			if (position == -1) {
				throw ClosedChannelException()
			}
			val bytesWritten = file.write(src, position)
			if (bytesWritten > 0) {
				position += bytesWritten
			}
			return bytesWritten
		}
	}
}