package dev.aspid812.virtualfs

import java.nio.ByteBuffer

interface VirtualFile {
	fun read(dst: ByteBuffer, position: Int): Int
	fun write(dst: ByteBuffer, position: Int): Int
}