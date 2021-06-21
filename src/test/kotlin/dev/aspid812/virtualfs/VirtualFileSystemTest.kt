package dev.aspid812.virtualfs

import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.io.path.createTempFile

class VirtualFileSystemTest {
	@Test
	fun `Reading and writing are consistent`() {
		val file = createTempFile("dta-", ".bin")
		val expectedData = byteArrayOf(1, 2, 3, 4, 5, 6)

		VirtualFileSystem.open(file).use { subj ->
			subj.openFileForWriting("foo").use { fileChannel ->
				val buffer = ByteBuffer.wrap(expectedData)
				fileChannel.write(buffer)
			}
		}

		VirtualFileSystem.open(file).use { subj ->
			subj.openFileForReading("foo").use { fileChannel ->
				val buffer = ByteBuffer.allocate(10)
				val bytesRead = fileChannel.read(buffer)
				Assert.assertEquals(expectedData.size, bytesRead)

				val actualData = ByteArray(bytesRead)
				buffer.flip().get(actualData)
				Assert.assertArrayEquals(expectedData, actualData)
			}
		}
	}
}