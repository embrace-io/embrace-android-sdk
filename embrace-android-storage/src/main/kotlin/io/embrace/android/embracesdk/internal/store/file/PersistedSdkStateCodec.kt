package io.embrace.android.embracesdk.internal.store.file

import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

/**
 * Reads and writes `persisted_sdk_state.bin`. This file contains a `PSDK` magic number followed by [PersistedSdkStateProto].
 */
object PersistedSdkStateCodec {

    private val MAGIC: ByteString = "PSDK".encodeUtf8()

    fun encode(proto: PersistedSdkStateProto, sink: BufferedSink) {
        sink.write(MAGIC)
        PersistedSdkStateProto.ADAPTER.encode(sink, proto)
    }

    /**
     * Throws [IOException] if the magic number is wrong or the message is truncated or malformed.
     */
    @Throws(IOException::class)
    fun decode(source: BufferedSource): PersistedSdkStateProto {
        if (!source.rangeEquals(0, MAGIC)) {
            throw IOException("Not a persisted SDK state file")
        }
        source.skip(MAGIC.size.toLong())
        return PersistedSdkStateProto.ADAPTER.decode(source)
    }
}
