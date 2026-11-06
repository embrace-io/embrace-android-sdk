package io.embrace.android.embracesdk.internal.perfetto.trace

import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class TraceValidatorTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val fs get() = fsRule.fileSystem

    @Test
    fun `the header a macrobenchmark writes is a perfetto trace`() {
        // the first bytes of every trace this repo's macrobenchmark has produced
        assertEquals(TraceFormat.PERFETTO, traceFormat(bytes(0x0a, 0x55, 0x32, 0x4e, 0x10, 0x06)))

        // a packet length spread over two varint bytes
        assertEquals(TraceFormat.PERFETTO, traceFormat(bytes(0x0a, 0xc8, 0x01, 0x08)))
    }

    @Test
    fun `anything that does not open like a packet is unknown`() {
        assertEquals(TraceFormat.UNKNOWN, traceFormat(ByteArray(0)))

        // no packet tag
        assertEquals(TraceFormat.UNKNOWN, traceFormat(bytes(0x50, 0x4b, 0x03, 0x04)))
        assertEquals(TraceFormat.UNKNOWN, traceFormat("{\"traceEvents\":[".toByteArray()))

        // a length varint that never terminates, and one cut short by the end of the file
        assertEquals(TraceFormat.UNKNOWN, traceFormat(bytes(0x0a, 0x80, 0x80, 0x80, 0x80, 0x80)))
        assertEquals(TraceFormat.UNKNOWN, traceFormat(bytes(0x0a)))
    }

    @Test
    fun `a trace is accepted in any of the containers one arrives in`() {
        assertEquals(TraceFormat.PERFETTO, validateTrace(fs, bundled("bundle.perfetto-trace", PERFETTO_HEADER)))
        assertEquals(TraceFormat.PERFETTO, validateTrace(fs, gzipped("trace.gz", PERFETTO_HEADER)))
        assertEquals(TraceFormat.PERFETTO, validateTrace(fs, write("raw", PERFETTO_HEADER)))
    }

    @Test
    fun `a container holding something other than a trace is still not a trace`() {
        val other = bytes(0xff, 0xd8, 0xff)
        assertEquals(TraceFormat.UNKNOWN, validateTrace(fs, bundled("bundle.perfetto-trace", other)))
        assertEquals(TraceFormat.UNKNOWN, validateTrace(fs, gzipped("other.gz", other)))
        assertEquals(TraceFormat.UNKNOWN, validateTrace(fs, write("other", other)))
    }

    @Test
    fun `a file that cannot be read is reported rather than thrown`() {
        assertEquals(TraceFormat.UNKNOWN, validateTrace(fs, write("empty", ByteArray(0))))
        assertEquals(TraceFormat.UNKNOWN, validateTrace(fs, "/not-a-file".toPath().also(fs::createDirectory)))
    }

    @Test
    fun `the committed macrobenchmark trace is a perfetto trace`() {
        assertEquals(TraceFormat.PERFETTO, validateTrace(FileSystem.RESOURCES, "/$FIXTURE".toPath()))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun write(name: String, content: ByteArray): Path = file(name) { write(content) }

    private fun gzipped(name: String, content: ByteArray): Path = file(name) {
        GZIPOutputStream(this).use { it.write(content) }
    }

    private fun bundled(name: String, content: ByteArray): Path = file(name) {
        ZipOutputStream(this).use { zip ->
            zip.putNextEntry(ZipEntry("Trace_output.pb"))
            zip.write(content)
            zip.closeEntry()
        }
    }

    private fun file(name: String, write: OutputStream.() -> Unit): Path = "/$name".toPath().also { path ->
        fs.write(path) { outputStream().write() }
    }

    private companion object {
        val PERFETTO_HEADER = byteArrayOf(0x0a, 0x55, 0x32, 0x4e)
        const val FIXTURE = "macrobenchmark-session-multi-file.perfetto.gz"
    }
}
