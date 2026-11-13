package io.embrace.android.embracesdk.internal.store.file

import okio.IOException

/**
 * `ordinals.bin`'s header is unusable: truncated, bad magic or an unsupported version.
 */
internal class CorruptOrdinalFileException(message: String) : IOException(message)
