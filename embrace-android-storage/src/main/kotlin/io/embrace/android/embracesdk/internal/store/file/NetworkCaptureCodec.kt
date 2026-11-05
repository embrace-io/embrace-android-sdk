package io.embrace.android.embracesdk.internal.store.file

import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

/**
 * Reads and writes `network_capture.bin`. This file contains a `NETC` magic number followed by [NetworkCaptureProto].
 */
object NetworkCaptureCodec {

    private val MAGIC: ByteString = "NETC".encodeUtf8()

    fun encode(proto: NetworkCaptureProto, sink: BufferedSink) {
        sink.write(MAGIC)
        NetworkCaptureProto.ADAPTER.encode(sink, proto)
    }

    /**
     * Throws [IOException] if the magic number is wrong or the message is truncated or malformed.
     */
    @Throws(IOException::class)
    fun decode(source: BufferedSource): NetworkCaptureProto {
        if (!source.rangeEquals(0, MAGIC)) {
            throw IOException("Not a network capture file")
        }
        source.skip(MAGIC.size.toLong())
        return NetworkCaptureProto.ADAPTER.decode(source)
    }
}
