package dev.aspid812.virtualfs

import dev.aspid812.virtualfs.mock.BlockStorageMock
import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

class VirtualFileSystemBackendTest {
	@Test
	fun `allocate() - Enough vacant blocks`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				firstVacantBlock = 1U
			}
			addDataBlock {
				nextDataBlock = 2U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val allocated = subj.allocate(1)

		Assert.assertEquals(1, allocated.size)
		Assert.assertEquals(1U, allocated[0])
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(0U, storage.readDataBlock(2U).nextDataBlock)
	}

	@Test
	fun `allocate() - Too few vacant blocks`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				firstVacantBlock = 1U
			}
			addDataBlock {
				nextDataBlock = 2U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val allocated = subj.allocate(3)

		Assert.assertEquals(3, allocated.size)
		Assert.assertEquals(1U, allocated[0])
		Assert.assertEquals(2U, allocated[1])
		Assert.assertEquals(3U, allocated[2])
		Assert.assertEquals(0U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
	}

	@Test
	fun `free() - Free multiple blocks`() {
		// Vacant block chain: 0 -> 1 -> 3 (-> 0)
		// Chain to be prepended: 2 -> 4 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				firstVacantBlock = 1U
			}
			addDataBlock {
				nextDataBlock = 3U
			}
			addDataBlock {
				nextDataBlock = 4U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.free(2U, 4U)

		// Vacant block chain: 0 -> 2 -> 4 -> 3 -> 1 (-> 0)
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(3U, storage.readDataBlock(1U).nextDataBlock)
		Assert.assertEquals(4U, storage.readDataBlock(2U).nextDataBlock)
		Assert.assertEquals(0U, storage.readDataBlock(3U).nextDataBlock)
		Assert.assertEquals(1U, storage.readDataBlock(4U).nextDataBlock)
	}

	@Test
	fun `free() - Free single block`() {
		// Vacant block chain: 0 -> 1 -> 3 (-> 0)
		// Chain to be prepended: 2 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				firstVacantBlock = 1U
			}
			addDataBlock {
				nextDataBlock = 3U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.free(2U, 2U)

		// Vacant block chain: 0 -> 2 -> 3 -> 1 (-> 0)
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(3U, storage.readDataBlock(1U).nextDataBlock)
		Assert.assertEquals(1U, storage.readDataBlock(2U).nextDataBlock)
		Assert.assertEquals(0U, storage.readDataBlock(3U).nextDataBlock)
	}

	@Test
	fun `free() - Initially empty vacant block chain`() {
		// Vacant block chain: 0 (-> 0)
		// Chain to be prepended: 1 -> 2 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				firstVacantBlock = 0U
			}
			addDataBlock {
				nextDataBlock = 2U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.free(1U, 2U)

		// Vacant block chain: 0 -> 1 -> 2 (-> 0)
		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(2U, storage.readDataBlock(1U).nextDataBlock)
		Assert.assertEquals(0U, storage.readDataBlock(2U).nextDataBlock)
	}

	@Test
	fun `buildIndex()`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 2U
			}
			addFileIndexBlock {
				nextIndexBlock = 2U
				prevIndexBlock = 0U
				fileSize = 22L
				fileName = "foo"
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 1U
				fileSize = 33L
				fileName = "bar"
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val index = subj.buildIndex()

		Assert.assertEquals(2, index.size)
		Assert.assertEquals("foo", index[0].name)
		Assert.assertEquals(22L, index[0].size)
		Assert.assertEquals(1U, index[0].handle)
		Assert.assertEquals("bar", index[1].name)
		Assert.assertEquals(33L, index[1].size)
		Assert.assertEquals(2U, index[1].handle)
	}

	@Test
	fun `createFile() - Empty file index`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {}
		}

		val subj = VirtualFileSystemBackend(storage)
		val created = subj.createFile("foo")

		Assert.assertEquals(created, storage.readHeaderIndexBlock(0U).nextIndexBlock)
		Assert.assertEquals(created, storage.readHeaderIndexBlock(0U).prevIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(created).nextIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(created).prevIndexBlock)
	}

	@Test
	fun `createFile() - Non-empty file index`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val created = subj.createFile("foo")

		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).nextIndexBlock)
		Assert.assertEquals(created, storage.readHeaderIndexBlock(0U).prevIndexBlock)
		Assert.assertEquals(created, storage.readFileIndexBlock(1U).nextIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(1U).prevIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(created).nextIndexBlock)
		Assert.assertEquals(1U, storage.readFileIndexBlock(created).prevIndexBlock)
	}

	@Test
	fun `createFile() - File attributes are set`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {}
		}

		val subj = VirtualFileSystemBackend(storage)
		val created = subj.createFile("foo")

		Assert.assertEquals(0L, storage.readFileIndexBlock(created).fileSize)
		Assert.assertEquals("foo", storage.readFileIndexBlock(created).fileName)
	}

	@Test
	fun `deleteFile() - Non-empty file`() {
		// File entry list: 0 <-> 2 <-> 1 (<-> 0)
		// Data block chains: 1 -> 3 (-> 0), 2 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 2U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 2U
				firstDataBlock = 3U
				lastDataBlock = 3U
			}
			addFileIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 0U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.deleteFile(1U)

		// File entry list: 0 <-> 2 (<-> 0)
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).nextIndexBlock)
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).prevIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(2U).nextIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(2U).prevIndexBlock)

		// Vacant block chain: 0 -> 1 -> 3 (-> 0)
		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(3U, storage.readDataBlock(1U).nextDataBlock)
		Assert.assertEquals(0U, storage.readDataBlock(3U).nextDataBlock)
	}

	@Test
	fun `deleteFile() - Empty file`() {
		// File entry list: 0 <-> 2 <-> 1 (<-> 0)
		// Data block chains: 1 -> 3 (-> 0), 2 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 2U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 2U
				firstDataBlock = 3U
				lastDataBlock = 3U
			}
			addFileIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 0U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.deleteFile(2U)

		// File entry list: 0 <-> 1 (<-> 0)
		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).nextIndexBlock)
		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).prevIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(1U).nextIndexBlock)
		Assert.assertEquals(0U, storage.readFileIndexBlock(1U).prevIndexBlock)

		// Vacant block chain: 0 -> 2 (-> 0)
		Assert.assertEquals(2U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(0U, storage.readDataBlock(2U).nextDataBlock)
	}

	@Test
	fun `deleteFile() - Last file remain`() {
		// File entry list: 0 <-> 1 (<-> 0)
		// Data block chains: 1 -> 2 (-> 0)
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
				firstDataBlock = 2U
				lastDataBlock = 2U
			}
			addDataBlock {
				nextDataBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		subj.deleteFile(1U)

		// File entry list: 0 (<-> 0)
		Assert.assertEquals(0U, storage.readHeaderIndexBlock(0U).nextIndexBlock)
		Assert.assertEquals(0U, storage.readHeaderIndexBlock(0U).prevIndexBlock)

		// Vacant block chain: 0 -> 1 -> 2 (-> 0)
		Assert.assertEquals(1U, storage.readHeaderIndexBlock(0U).firstVacantBlock)
		Assert.assertEquals(2U, storage.readDataBlock(1U).nextDataBlock)
		Assert.assertEquals(0U, storage.readDataBlock(2U).nextDataBlock)
	}

	@Test
	fun `loadFile() - Regular case`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
				fileSize = 6L
				firstDataBlock = 2U
				lastDataBlock = 3U
			}
			addDataBlock {
				nextDataBlock = 3U
				fileData = byteArrayOf(1, 2, 3)
			}
			addDataBlock {
				nextDataBlock = 0U
				fileData = byteArrayOf(4, 5, 6)
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val loadedData = subj.loadFile(1U)

		Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), loadedData)
	}

	@Test
	fun `loadFile() - Buffer is smaller than the file`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
				fileSize = 6L
				firstDataBlock = 2U
				lastDataBlock = 3U
			}
			addDataBlock {
				nextDataBlock = 3U
				fileData = byteArrayOf(1, 2, 3)
			}
			addDataBlock {
				nextDataBlock = 0U
				fileData = byteArrayOf(4, 5, 6)
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val loadedData = subj.loadFile(1U)

		Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4), loadedData)
	}

	@Test
	fun `loadFile() - Buffer is larger than the file`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
				fileSize = 6L
				firstDataBlock = 2U
				lastDataBlock = 3U
			}
			addDataBlock {
				nextDataBlock = 3U
				fileData = byteArrayOf(1, 2, 3)
			}
			addDataBlock {
				nextDataBlock = 0U
				fileData = byteArrayOf(4, 5, 6)
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val loadedData = subj.loadFile(1U)

		Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), loadedData)
	}

	@Test
	fun `storeFile()`() {
		val storage = BlockStorageMock().apply {
			addHeaderIndexBlock {
				nextIndexBlock = 1U
				prevIndexBlock = 1U
			}
			addFileIndexBlock {
				nextIndexBlock = 0U
				prevIndexBlock = 0U
			}
		}

		val subj = VirtualFileSystemBackend(storage)
		val storedData = byteArrayOf(1, 2, 3, 4, 5, 6)
		subj.storeFile(1U, storedData, 0, storedData.size)

		Assert.assertEquals(6, storage.readFileIndexBlock(1U).fileSize)
		Assert.assertEquals(2U, storage.readFileIndexBlock(1U).firstDataBlock)
		Assert.assertEquals(3U, storage.readFileIndexBlock(1U).lastDataBlock)
		Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4), storage.readDataBlock(2U).fileData)
		Assert.assertArrayEquals(byteArrayOf(5, 6), storage.readDataBlock(3U).fileData)
	}
}