package io.embrace.android.gradle.plugin.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.OutputStream

class StreamedRequestBody(
    private val mediaType: MediaType,
    private val serializationAction: (stream: OutputStream) -> Unit
) : RequestBody() {
    override fun contentType() = mediaType

    override fun writeTo(sink: BufferedSink) {
        sink.outputStream().buffered().use(serializationAction)
    }
}
