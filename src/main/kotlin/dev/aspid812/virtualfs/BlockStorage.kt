package dev.aspid812.virtualfs

import java.io.Closeable

interface BlockStorage : Closeable {
	val maxBytesPerDataBlock: Int

	fun expand(numExtraBlocks: Int): UInt

	fun newHeaderIndexBlock(): HeaderIndexBlock
	fun newFileIndexBlock(): FileIndexBlock
	fun newDataBlock(): DataBlock

	fun readHeaderIndexBlock(ref: UInt): HeaderIndexBlock
	fun readFileIndexBlock(ref: UInt): FileIndexBlock
	fun readDataBlock(ref: UInt): DataBlock

	fun writeHeaderIndexBlock(ref: UInt, block: HeaderIndexBlock)
	fun writeFileIndexBlock(ref: UInt, block: FileIndexBlock)
	fun writeDataBlock(ref: UInt, block: DataBlock)
}