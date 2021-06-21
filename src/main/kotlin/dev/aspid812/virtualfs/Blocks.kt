package dev.aspid812.virtualfs

sealed class Block(
	val rawBytes: ByteArray
)

class HeaderIndexBlock(rawBytes: ByteArray) : Block(rawBytes) {
	companion object {
		const val SIGNATURE = 0x44544131
	}

	var nextIndexBlock: UInt = 0U
	var prevIndexBlock: UInt = 0U
	var firstVacantBlock: UInt = 0U
	var blockSize: Int = 0
}

class FileIndexBlock(rawBytes: ByteArray) : Block(rawBytes) {
	companion object {
		const val SIGNATURE = 0x44544649
	}

	var nextIndexBlock: UInt = 0U
	var prevIndexBlock: UInt = 0U
	var firstDataBlock: UInt = 0U
	var lastDataBlock: UInt = 0U
	var fileSize: Long = 0L
	var fileName: String = ""
}

class DataBlock(rawBytes: ByteArray) : Block(rawBytes) {
	companion object {
		const val SIGNATURE = 0x44544644
		private val NO_DATA = ByteArray(0)
	}
	var nextDataBlock: UInt = 0U
	var fileData: ByteArray = NO_DATA
}