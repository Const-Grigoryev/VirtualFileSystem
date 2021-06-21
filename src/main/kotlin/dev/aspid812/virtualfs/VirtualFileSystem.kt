package dev.aspid812.virtualfs

import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.nio.file.Path

class VirtualFileSystem {
	companion object {
		private const val DEFAULT_BLOCK_SIZE = 512

		fun open(path: Path): VirtualFileSystemFrontend {
			val channel = FileChannel.open(path, READ, WRITE, CREATE)
			val storage: VirtualFileSystemStorage
			if (channel.size() > 0) {
				val header = VirtualFileSystemStorage.readMinimalHeader(channel)
				storage = VirtualFileSystemStorage(header.blockSize, channel)
			}
			else {
				storage = VirtualFileSystemStorage(DEFAULT_BLOCK_SIZE, channel)
				val header = storage.newHeaderIndexBlock()
				header.blockSize = DEFAULT_BLOCK_SIZE
				storage.expand(1)
				storage.writeHeaderIndexBlock(0U, header)
			}
			val backend = VirtualFileSystemBackend(storage)
			val frontend = VirtualFileSystemFrontend(backend)
			return frontend
		}
	}
}