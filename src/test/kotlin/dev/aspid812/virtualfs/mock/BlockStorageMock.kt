package dev.aspid812.virtualfs.mock

import dev.aspid812.virtualfs.*

class BlockStorageMock : BlockStorage {

	override val maxBytesPerDataBlock = 4

	val blocks = ArrayList<Block?>()

	override fun close() {}

	override fun expand(numExtraBlocks: Int): UInt {
		require(numExtraBlocks >= 0)

		val firstAddedBlockRef = blocks.size.toUInt()
		var numBlocksAdded = 0
		while (numBlocksAdded < numExtraBlocks) {
			blocks.add(null)
			numBlocksAdded++
		}

		return firstAddedBlockRef
	}

	override fun newHeaderIndexBlock(): HeaderIndexBlock {
		return HeaderIndexBlock(ByteArray(32))
	}

	override fun newFileIndexBlock(): FileIndexBlock {
		return FileIndexBlock(ByteArray(32))
	}

	override fun newDataBlock(): DataBlock {
		return DataBlock(ByteArray(32))
	}

	fun addHeaderIndexBlock(init: HeaderIndexBlock.() -> Unit) {
		val block = newHeaderIndexBlock()
		block.init()
		blocks.add(block)
	}

	fun addFileIndexBlock(init: FileIndexBlock.() -> Unit) {
		val block = newFileIndexBlock()
		block.init()
		blocks.add(block)
	}

	fun addDataBlock(init: DataBlock.() -> Unit) {
		val block = newDataBlock()
		block.init()
		blocks.add(block)
	}

	override fun readHeaderIndexBlock(ref: UInt): HeaderIndexBlock {
		return blocks[ref.toInt()] as HeaderIndexBlock
	}

	override fun readFileIndexBlock(ref: UInt): FileIndexBlock  {
		return blocks[ref.toInt()] as FileIndexBlock
	}

	override fun readDataBlock(ref: UInt): DataBlock  {
		return blocks[ref.toInt()] as DataBlock
	}


	override fun writeHeaderIndexBlock(ref: UInt, block: HeaderIndexBlock) {
		blocks[ref.toInt()] = block
	}

	override fun writeFileIndexBlock(ref: UInt, block: FileIndexBlock) {
		blocks[ref.toInt()] = block
	}

	override fun writeDataBlock(ref: UInt, block: DataBlock) {
		blocks[ref.toInt()] = block
	}
}