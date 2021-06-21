package dev.aspid812.virtualfs

import dev.aspid812.virtualfs.mock.SeekableByteChannelMock
import org.junit.Assert
import org.junit.Test

class VirtualFileSystemStorageTest {
	@Test
	fun `readHeaderIndexBlock() and writeHeaderIndexBlock()`() {
		val channel = SeekableByteChannelMock(40)
		val subj = VirtualFileSystemStorage(40, channel)

		val expectedBlock = subj.newHeaderIndexBlock().apply {
			this.nextIndexBlock = 11U
			this.prevIndexBlock = 22U
			this.firstVacantBlock = 33U
			this.blockSize = 44
		}

		subj.writeHeaderIndexBlock(0U, expectedBlock)
		val actualBlock = subj.readHeaderIndexBlock(0U)

		Assert.assertEquals(expectedBlock.nextIndexBlock, actualBlock.nextIndexBlock)
		Assert.assertEquals(expectedBlock.prevIndexBlock, actualBlock.prevIndexBlock)
		Assert.assertEquals(expectedBlock.firstVacantBlock, actualBlock.firstVacantBlock)
		Assert.assertEquals(expectedBlock.blockSize, actualBlock.blockSize)
	}

	@Test
	fun `readFileIndexBlock() and writeFileIndexBlock()`() {
		val channel = SeekableByteChannelMock(64)
		val subj = VirtualFileSystemStorage(64, channel)

		val expectedBlock = subj.newFileIndexBlock().apply {
			this.nextIndexBlock = 11U
			this.prevIndexBlock = 22U
			this.firstDataBlock = 33U
			this.lastDataBlock = 44U
			this.fileSize = 55L
			this.fileName = "foo"
		}

		subj.writeFileIndexBlock(0U, expectedBlock)
		val actualBlock = subj.readFileIndexBlock(0U)

		Assert.assertEquals(expectedBlock.nextIndexBlock, actualBlock.nextIndexBlock)
		Assert.assertEquals(expectedBlock.prevIndexBlock, actualBlock.prevIndexBlock)
		Assert.assertEquals(expectedBlock.firstDataBlock, actualBlock.firstDataBlock)
		Assert.assertEquals(expectedBlock.firstDataBlock, actualBlock.firstDataBlock)
		Assert.assertEquals(expectedBlock.fileSize, actualBlock.fileSize)
		Assert.assertEquals(expectedBlock.fileName, actualBlock.fileName)
	}

	@Test
	fun `readDataBlock() and writeDataBlock()`() {
		val channel = SeekableByteChannelMock(40)
		val subj = VirtualFileSystemStorage(40, channel)

		val expectedBlock = subj.newDataBlock().apply {
			nextDataBlock = 11U;
			fileData = byteArrayOf(1, 2, 3)
		}

		subj.writeDataBlock(0U, expectedBlock)
		val actualBlock = subj.readDataBlock(0U)

		Assert.assertEquals(expectedBlock.nextDataBlock, actualBlock.nextDataBlock)
		Assert.assertArrayEquals(expectedBlock.fileData, actualBlock.fileData)
	}

}