package dev.aspid812.virtualfs

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.zip.CRC32

class VirtualFileSystemStorage internal constructor(
	private val blockSize: Int,
	private val channel: SeekableByteChannel
) : BlockStorage, Closeable {
	companion object {
		const val BLOCK_HEADER_SIZE = 8
		const val MIN_HEADER_INDEX_BLOCK_BODY_SIZE = 16
		const val MIN_FILE_INDEX_BLOCK_BODY_SIZE = 28
		const val MIN_DATA_BLOCK_BODY_SIZE = 8

		fun readMinimalHeader(channel: SeekableByteChannel): HeaderIndexBlock {
			val blockSize = BLOCK_HEADER_SIZE + MIN_HEADER_INDEX_BLOCK_BODY_SIZE
			val buffer = ByteBuffer.allocate(blockSize)
			val bytesRead = channel.read(buffer)
			if (bytesRead < blockSize) {
				throw VirtualFileSystemException("Unexpected end of file")
			}

			buffer.flip()
			val signature = buffer.getInt()
			if (signature != HeaderIndexBlock.SIGNATURE) {
				throw VirtualFileSystemException("Block signature mismatch")
			}

			val checksum = buffer.getInt()
			val block = HeaderIndexBlock(ByteArray(0))
			return parseHeaderIndexBlock(block, buffer)
		}

		private fun parseHeaderIndexBlock(block: HeaderIndexBlock, bodyBuffer: ByteBuffer): HeaderIndexBlock {
			block.nextIndexBlock = bodyBuffer.getInt().toUInt()
			block.prevIndexBlock = bodyBuffer.getInt().toUInt()
			block.firstVacantBlock = bodyBuffer.getInt().toUInt()
			block.blockSize = bodyBuffer.getInt()
			return block
		}

		private fun renderHeaderIndexBlock(block: HeaderIndexBlock, bodyBuffer: ByteBuffer): ByteBuffer {
			bodyBuffer.putInt(block.nextIndexBlock.toInt())
			bodyBuffer.putInt(block.prevIndexBlock.toInt())
			bodyBuffer.putInt(block.firstVacantBlock.toInt())
			bodyBuffer.putInt(block.blockSize)
			return bodyBuffer
		}

		private fun parseFileIndexBlock(block: FileIndexBlock, bodyBuffer: ByteBuffer): FileIndexBlock {
			block.nextIndexBlock = bodyBuffer.getInt().toUInt()
			block.prevIndexBlock = bodyBuffer.getInt().toUInt()
			block.firstDataBlock = bodyBuffer.getInt().toUInt()
			block.lastDataBlock = bodyBuffer.getInt().toUInt()
			block.fileSize = bodyBuffer.getLong()

			val fileNameSize = bodyBuffer.getInt()
			if (fileNameSize > bodyBuffer.remaining()) {
				throw VirtualFileSystemException("File is corrupted")
			}
			bodyBuffer.limit(bodyBuffer.position() + fileNameSize)
			block.fileName = Charsets.UTF_16BE.decode(bodyBuffer).toString()

			return block
		}

		private fun renderFileIndexBlock(block: FileIndexBlock, bodyBuffer: ByteBuffer): ByteBuffer {
			bodyBuffer.putInt(block.nextIndexBlock.toInt())
			bodyBuffer.putInt(block.prevIndexBlock.toInt())
			bodyBuffer.putInt(block.firstDataBlock.toInt())
			bodyBuffer.putInt(block.lastDataBlock.toInt())
			bodyBuffer.putLong(block.fileSize)

			val encodedFileName = Charsets.UTF_16BE.encode(block.fileName)
			val fileNameSize = encodedFileName.remaining()
			bodyBuffer.putInt(fileNameSize)
			if (fileNameSize > bodyBuffer.remaining()) {
				throw VirtualFileSystemException("File name is too long")
			}
			bodyBuffer.put(encodedFileName)

			return bodyBuffer
		}

		private fun parseDataBlock(block: DataBlock, bodyBuffer: ByteBuffer): DataBlock {
			block.nextDataBlock = bodyBuffer.getInt().toUInt()

			val dataSize = bodyBuffer.getInt()
			if (dataSize > bodyBuffer.remaining()) {
				throw VirtualFileSystemException("File is corrupted")
			}
			bodyBuffer.limit(bodyBuffer.position() + dataSize)
			block.fileData = ByteArray(dataSize)
			bodyBuffer.get(block.fileData)

			return block
		}

		private fun renderDataBlock(block: DataBlock, bodyBuffer: ByteBuffer): ByteBuffer {
			bodyBuffer.putInt(block.nextDataBlock.toInt())

			val dataSize = block.fileData.size
			bodyBuffer.putInt(dataSize)
			if (dataSize > bodyBuffer.remaining()) {
				throw VirtualFileSystemException("Data portion is too large")
			}
			bodyBuffer.put(block.fileData)

			return bodyBuffer
		}
	}

	private var numBlocksInStorage: Int = (channel.size() / blockSize).toInt()

	override val maxBytesPerDataBlock = blockSize - BLOCK_HEADER_SIZE - MIN_DATA_BLOCK_BODY_SIZE

	override fun close() {
		channel.close()
	}

	override fun expand(numExtraBlocks: Int): UInt {
		val firstExtraBlock = numBlocksInStorage.toUInt()
		numBlocksInStorage += numExtraBlocks
		return firstExtraBlock
	}

	private fun readBlock(ref: UInt, expectedSignature: Int): ByteArray {
		val buffer = ByteBuffer.allocate(blockSize)
		val bytesRead = channel.position(ref.toLong() * blockSize).read(buffer)
		if (bytesRead < blockSize) {
			throw VirtualFileSystemException("Unexpected end of file")
		}

		buffer.flip()
		val actualSignature = buffer.getInt()
		if (actualSignature != expectedSignature) {
			throw VirtualFileSystemException("Block signature mismatch")
		}

		val expectedChecksum = buffer.getInt().toUInt()
		val actualChecksum = CRC32()
		buffer.mark()
		actualChecksum.update(buffer)
		buffer.reset()
		if (actualChecksum.value.toUInt() != expectedChecksum) {
			throw VirtualFileSystemException("File is corrupted: checksum mismatch")
		}

		val body = ByteArray(buffer.remaining())
		buffer.get(body)
		return body
	}

	private fun writeBlock(ref: UInt, signature: Int, body: ByteArray) {
		require(body.size == blockSize - BLOCK_HEADER_SIZE)

		val buffer = ByteBuffer.allocate(blockSize)
		buffer.putInt(signature)

		val checksum = CRC32()
		checksum.update(body)
		buffer.putInt(checksum.value.toInt())

		buffer.put(body)

		buffer.flip()
		channel.position(ref.toLong() * blockSize).write(buffer)
	}

	override fun newHeaderIndexBlock(): HeaderIndexBlock {
		return HeaderIndexBlock(ByteArray(blockSize - BLOCK_HEADER_SIZE))
	}

	override fun readHeaderIndexBlock(ref: UInt): HeaderIndexBlock {
		val body = readBlock(ref, HeaderIndexBlock.SIGNATURE)
		val block = HeaderIndexBlock(body)
		val bodyBuffer = ByteBuffer.wrap(body)
		return parseHeaderIndexBlock(block, bodyBuffer)
	}

	override fun writeHeaderIndexBlock(ref: UInt, block: HeaderIndexBlock) {
		val body = block.rawBytes
		val bodyBuffer = ByteBuffer.wrap(body)
		renderHeaderIndexBlock(block, bodyBuffer)
		writeBlock(ref, HeaderIndexBlock.SIGNATURE, body)
	}

	override fun newFileIndexBlock(): FileIndexBlock {
		return FileIndexBlock(ByteArray(blockSize - BLOCK_HEADER_SIZE))
	}

	override fun readFileIndexBlock(ref: UInt): FileIndexBlock {
		val body = readBlock(ref, FileIndexBlock.SIGNATURE)
		val block = FileIndexBlock(body)
		val bodyBuffer = ByteBuffer.wrap(body)
		return parseFileIndexBlock(block, bodyBuffer)
	}

	override fun writeFileIndexBlock(ref: UInt, block: FileIndexBlock) {
		val body = block.rawBytes
		val bodyBuffer = ByteBuffer.wrap(body)
		renderFileIndexBlock(block, bodyBuffer)
		writeBlock(ref, FileIndexBlock.SIGNATURE, body)
	}

	override fun newDataBlock(): DataBlock {
		return DataBlock(ByteArray(blockSize - BLOCK_HEADER_SIZE))
	}

	override fun readDataBlock(ref: UInt): DataBlock {
		val body = readBlock(ref, DataBlock.SIGNATURE)
		val block = DataBlock(body)
		val bodyBuffer = ByteBuffer.wrap(body)
		return parseDataBlock(block, bodyBuffer)
	}

	override fun writeDataBlock(ref: UInt, block: DataBlock) {
		val body = block.rawBytes
		val bodyBuffer = ByteBuffer.wrap(body)
		renderDataBlock(block, bodyBuffer)
		writeBlock(ref, DataBlock.SIGNATURE, body)
	}
}