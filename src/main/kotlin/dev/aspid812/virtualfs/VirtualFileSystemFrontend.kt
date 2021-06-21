package dev.aspid812.virtualfs

import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VirtualFileSystemFrontend(
	private val backend: VirtualFileSystemBackend
): Closeable {
	private val lock = ReentrantLock()

	private val files = buildFileIndex()

	private inner class VirtualFileEntity(
		val handle: FileHandle
	) : AbstractVirtualFileEntity() {
		override fun load(): ByteArray {
			return lock.withLock {
				backend.loadFile(handle)
			}
		}

		override fun store(content: ByteArray, offset: Int, length: Int) {
			lock.withLock {
				backend.storeFile(handle, content, offset, length)
			}
		}
	}

	private fun buildFileIndex(): MutableMap<String, VirtualFileEntity> {
		val index = HashMap<String, VirtualFileEntity>()
		index.putAll(backend.buildIndex().map { file ->
			Pair(file.name, VirtualFileEntity(file.handle))
		})
		return index
	}

	override fun close() {
		backend.close()
	}

	fun createFile(fileName: String) {
		lock.withLock {
			if (files.containsKey(fileName)) {
				throw FileAlreadyExistsException(fileName)
			}
			val fileHandle = backend.createFile(fileName)
			files.put(fileName, VirtualFileEntity(fileHandle))
		}
	}

	fun deleteFile(fileName: String) {
		lock.withLock {
			val file = files.getOrElse(fileName) {
				throw FileNotFoundException(fileName)
			}
			if (file.isOpen()) {
				throw IOException("The file is open right now and thus cannot be deleted")
			}
			backend.deleteFile(file.handle)
			files.remove(fileName)
		}
	}

	fun openFileForReading(fileName: String): VirtualFileChannel {
		lock.withLock {
			val file = files.getOrElse(fileName) {
				throw FileNotFoundException(fileName)
			}
			return object: VirtualFileChannel(file.open(), true) {
				override fun doClose() {
					file.close()
				}
			}
		}
	}

	fun openFileForWriting(fileName: String): VirtualFileChannel {
		lock.withLock {
			val file = files.computeIfAbsent(fileName) {
				val handle = backend.createFile(fileName)
				VirtualFileEntity(handle)
			}
			return object: VirtualFileChannel(file.open(), false) {
				override fun doClose() {
					file.close()
				}
			}
		}
	}
}