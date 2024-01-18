package io.embrace.android.embracesdk.internal.compression

import io.embrace.android.embracesdk.comms.api.SerializationAction
import java.io.File
import java.io.OutputStream

/**
 * Interface for compressing data.
 */
internal interface Compressor {

    /**
     * Compresses the data from the SerializationAction and writes it to the OutputStream.
     */
    fun compress(outputStream: OutputStream, action: SerializationAction)

    /**
     * Verifies if the file is compressed.
     */
    fun isCompressed(file: File): Boolean
}
