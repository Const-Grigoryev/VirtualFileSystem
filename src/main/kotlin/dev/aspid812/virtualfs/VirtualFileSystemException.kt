package dev.aspid812.virtualfs

import java.io.IOException

class VirtualFileSystemException(
	message: String? = null,
	cause: Throwable? = null
) : IOException(message, cause)