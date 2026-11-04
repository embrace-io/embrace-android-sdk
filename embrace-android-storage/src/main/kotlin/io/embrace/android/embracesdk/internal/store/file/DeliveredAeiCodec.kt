package io.embrace.android.embracesdk.internal.store.file

import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

/**
 * Reads and writes `aei_delivered.bin`. This file contains an `AEID` magic number followed by [DeliveredAeiProto].
 */
object DeliveredAeiCodec {

    private val MAGIC: ByteString = "AEID".encodeUtf8()

    fun encode(proto: DeliveredAeiProto, sink: BufferedSink) {
        sink.write(MAGIC)
        DeliveredAeiProto.ADAPTER.encode(sink, proto)
    }

    /**
     * Throws [IOException] if the magic number is wrong or the message is truncated or malformed.
     */
    @Throws(IOException::class)
    fun decode(source: BufferedSource): DeliveredAeiProto {
        if (!source.rangeEquals(0, MAGIC)) {
            throw IOException("Not a delivered AEI file")
        }
        source.skip(MAGIC.size.toLong())
        return DeliveredAeiProto.ADAPTER.decode(source)
    }
}
