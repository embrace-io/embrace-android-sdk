package io.embrace.android.embracesdk

import okio.BufferedSource
import okio.ByteString
import okio.buffer
import okio.source
import java.io.InputStream

object ResourceReader {
    fun readResource(name: String): InputStream {
        val classLoader = checkNotNull(javaClass.classLoader)
        return classLoader.getResourceAsStream(name)
            ?: error("Could not find resource '$name'")
    }

    fun readResourceAsSource(name: String): BufferedSource = readResource(name).source().buffer()

    fun readResourceAsByteString(name: String): ByteString = readResourceAsSource(name).use(BufferedSource::readByteString)

    fun readResourceAsText(name: String): String = readResourceAsSource(name).use(BufferedSource::readUtf8)
}
