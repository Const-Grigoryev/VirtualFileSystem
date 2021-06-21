package dev.aspid812.virtualfs

import java.io.Closeable
import java.nio.ByteBuffer

typealias FileHandle = UInt

class VirtualFileSystemBackend internal constructor(
	val storage: BlockStorage
) : Closeable {
	override fun close() {
		storage.close()
	}

	internal fun allocate(numBlocksDemand: Int): List<UInt> {
		val allocatedBlocks = ArrayList<UInt>(numBlocksDemand)
		var numBlocksAllocated = 0

		// Allocate vacant blocks at first
		val header = storage.readHeaderIndexBlock(0U)
		var blockRef = header.firstVacantBlock
		while (numBlocksAllocated < numBlocksDemand && blockRef != 0U) {
			val block = storage.readDataBlock(blockRef)
			allocatedBlocks.add(blockRef)
			numBlocksAllocated++
			blockRef = block.nextDataBlock
		}
		header.firstVacantBlock = blockRef
		storage.writeHeaderIndexBlock(0U, header)

		// If there is too few vacant blocks, create new ones at the storage end
		if (numBlocksAllocated < numBlocksDemand) {
			blockRef = storage.expand(numBlocksDemand - numBlocksAllocated)
			while (numBlocksAllocated < numBlocksDemand) {
				allocatedBlocks.add(blockRef)
				numBlocksAllocated++
				blockRef++
			}
		}

		return allocatedBlocks
	}

	internal fun free(firstRef: UInt, lastRef: UInt) {
		val header = storage.readHeaderIndexBlock(0U)

		val lastBlock = storage.readDataBlock(lastRef)
		lastBlock.nextDataBlock = header.firstVacantBlock
		storage.writeDataBlock(lastRef, lastBlock)

		header.firstVacantBlock = firstRef
		storage.writeHeaderIndexBlock(0U, header)
	}

	fun buildIndex(): List<VirtualFileInfo> {
		val header = storage.readHeaderIndexBlock(0U)
		val index = ArrayList<VirtualFileInfo>()

		var fileHandle = header.nextIndexBlock
		while (fileHandle != 0U) {
			val entry = storage.readFileIndexBlock(fileHandle)
			index.add(VirtualFileInfo(entry.fileName, entry.fileSize, fileHandle))
			fileHandle = entry.nextIndexBlock
		}
		return index
	}

	fun createFile(fileName: String): UInt {
		val header = storage.readHeaderIndexBlock(0U)
		val nextRef = 0U
		val prevRef = header.prevIndexBlock

		// Create file entry and insert it in the file index
		val fileHandle = allocate(1).first()
		val entry = storage.newFileIndexBlock()
		entry.nextIndexBlock = nextRef
		entry.prevIndexBlock = prevRef
		entry.fileSize = 0L
		entry.fileName = fileName
		storage.writeFileIndexBlock(fileHandle, entry)

		if (prevRef != 0U) {
			val prevBlock = storage.readFileIndexBlock(prevRef)
			prevBlock.nextIndexBlock = fileHandle
			storage.writeFileIndexBlock(prevRef, prevBlock)
		}
		else {
			header.nextIndexBlock = fileHandle
		}
		header.prevIndexBlock = fileHandle
		storage.writeHeaderIndexBlock(0U, header)

		return fileHandle
	}

	fun deleteFile(fileHandle: FileHandle) {
		val entry = storage.readFileIndexBlock(fileHandle)

		// Remove file entry from the file index
		val prevRef = entry.prevIndexBlock
		val nextRef = entry.nextIndexBlock
		if (prevRef == nextRef) {
			// `prevRef == nextRef` imply `prevRef == nextRef == 0U`
			val header = storage.readHeaderIndexBlock(0U)
			header.nextIndexBlock = 0U
			header.prevIndexBlock = 0U
			storage.writeHeaderIndexBlock(0U, header)
		}
		else {
			if (prevRef == 0U) {
				val prevBlock = storage.readHeaderIndexBlock(prevRef)
				prevBlock.nextIndexBlock = nextRef
				storage.writeHeaderIndexBlock(prevRef, prevBlock)
			}
			else {
				val prevBlock = storage.readFileIndexBlock(prevRef)
				prevBlock.nextIndexBlock = nextRef
				storage.writeFileIndexBlock(prevRef, prevBlock)
			}

			if (nextRef == 0U) {
				val nextBlock = storage.readHeaderIndexBlock(nextRef)
				nextBlock.prevIndexBlock = prevRef
				storage.writeHeaderIndexBlock(nextRef, nextBlock)
			}
			else {
				val nextBlock = storage.readFileIndexBlock(nextRef)
				nextBlock.prevIndexBlock = prevRef
				storage.writeFileIndexBlock(nextRef, nextBlock)
			}
		}

		// Prepend the chain of file data blocks with the file index block...
		val firstBlock = storage.newDataBlock()
		firstBlock.nextDataBlock = entry.firstDataBlock
		storage.writeDataBlock(fileHandle, firstBlock)

		// ...and release this chain at last
		if (entry.firstDataBlock != 0U && entry.lastDataBlock != 0U) {
			free(fileHandle, entry.lastDataBlock)
		}
		else if (entry.firstDataBlock == 0U && entry.lastDataBlock == 0U) {
			free(fileHandle, fileHandle)
		}
		else {
			throw VirtualFileSystemException()
		}
	}

	fun loadFile(fileHandle: FileHandle): ByteArray {
		val entry = storage.readFileIndexBlock(fileHandle)
		val fileBytes = ByteArray(entry.fileSize.toInt())

		var buffer = ByteBuffer.wrap(fileBytes)
		var blockRef = entry.firstDataBlock
		while (blockRef != 0U && buffer.hasRemaining()) {
			val block = storage.readDataBlock(blockRef)
			val portion = minOf(buffer.remaining(), block.fileData.size)
			buffer.put(block.fileData, 0, portion)
			blockRef = block.nextDataBlock
		}

		return fileBytes
	}

	fun storeFile(fileHandle: FileHandle, fileBytes: ByteArray, offset: Int, length: Int) {
		val entry = storage.readFileIndexBlock(fileHandle)

		if (entry.firstDataBlock != 0U) {
			free(entry.firstDataBlock, entry.lastDataBlock)
		}

		val buffer = ByteBuffer.wrap(fileBytes, offset, length)
		val fileSize = buffer.remaining()
		if (fileSize > 0) {
			val numDataBlocks = 1 + (fileSize - 1) / storage.maxBytesPerDataBlock
			val dataBlockRefs = allocate(numDataBlocks)

			for (k in 0 until numDataBlocks) {
				val blockRef = dataBlockRefs[k]
				val nextBlockRef = if (k < numDataBlocks - 1) dataBlockRefs[k + 1] else 0U
				val blockDataSize = if (k < numDataBlocks - 1) storage.maxBytesPerDataBlock else buffer.remaining()

				val data = ByteArray(blockDataSize)
				buffer.get(data)

				val block = storage.newDataBlock()
				block.nextDataBlock = nextBlockRef
				block.fileData = data
				storage.writeDataBlock(blockRef, block)
			}

			entry.fileSize = fileSize.toLong()
			entry.firstDataBlock = dataBlockRefs.first()
			entry.lastDataBlock = dataBlockRefs.last()
			storage.writeFileIndexBlock(fileHandle, entry)
		}
		else {
			entry.fileSize = 0L
			entry.firstDataBlock = 0U
			entry.lastDataBlock = 0U
			storage.writeFileIndexBlock(fileHandle, entry)
		}
	}
}