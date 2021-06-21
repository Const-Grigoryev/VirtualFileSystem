package dev.aspid812.virtualfs

data class VirtualFileInfo(
	val name: String,
	val size: Long,
	val handle: FileHandle
)
