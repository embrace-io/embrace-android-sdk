package io.embrace.android.embracesdk.internal.store.file

import okio.IOException

/**
 * `ordinals.bin` has the wrong magic.
 */
internal class CorruptOrdinalFileException(message: String) : IOException(message)
