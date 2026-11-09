package io.embrace.analysis.perfetto

import com.sun.net.httpserver.HttpServer
import io.embrace.analysis.perfetto.TraceProcessorShell.Platform
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class TraceProcessorShellTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var requests: AtomicInteger
    private lateinit var log: ByteArrayOutputStream
    private lateinit var cacheDir: Path

    @Before
    fun setUp() {
        requests = AtomicInteger()
        log = ByteArrayOutputStream()
        cacheDir = tmp.newFolder("cache").toPath()
        // Release archives as Perfetto lays them out: the host tools under a directory named for the platform. The
        // mac-amd64 one is missing the binary.
        val archives = mapOf(
            "/v1.2/mac-arm64.zip" to zipOf("mac-arm64/LICENSE" to LICENSE, "mac-arm64/trace_processor_shell" to BINARY),
            "/v1.2/mac-amd64.zip" to zipOf("mac-amd64/LICENSE" to LICENSE),
        )
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val archive = archives[exchange.requestURI.path]
                if (archive != null) {
                    exchange.sendResponseHeaders(200, archive.size.toLong())
                    exchange.responseBody.use { it.write(archive) }
                } else {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
            }
            start()
        }
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `the binary is extracted from the release's v-prefixed archive into the cache, executable, and announced`() {
        val binary = shell().install(Platform.MAC_ARM64)

        assertEquals(cacheDir.resolve("v1.2/mac-arm64/trace_processor_shell"), binary)
        assertArrayEquals(BINARY, Files.readAllBytes(binary))
        assertTrue(Files.isExecutable(binary))
        assertEquals("downloading trace_processor_shell v1.2 for mac-arm64\n", log.toString(Charsets.UTF_8))
    }

    @Test
    fun `a cached copy is used without downloading again`() {
        shell().install(Platform.MAC_ARM64)
        shell().install(Platform.MAC_ARM64)

        assertEquals(1, requests.get())
    }

    @Test
    fun `an archive without the binary is refused and leaves nothing in the cache`() {
        val error = assertThrows(IllegalStateException::class.java) { shell().install(Platform.MAC_AMD64) }

        assertEquals("the v1.2 archive for mac-amd64 has no mac-amd64/trace_processor_shell", error.message)
        assertEquals(emptyList<Path>(), Files.list(cacheDir.resolve("v1.2/mac-amd64")).use { it.toList() })
    }

    @Test
    fun `a release that isn't published fails the download and leaves nothing in the cache`() {
        assertThrows(IOException::class.java) { shell(version = "9.9").install(Platform.MAC_ARM64) }

        assertEquals(emptyList<Path>(), Files.list(cacheDir.resolve("v9.9/mac-arm64")).use { it.toList() })
    }

    @Test
    fun `the pinned version is the catalog's bare version, without Perfetto's v`() {
        assertTrue(TraceProcessorShell.pinnedVersion().matches(Regex("\\d+(\\.\\d+)*")))
    }

    @Test
    fun `the platform is macOS on the JVM's architecture, and nothing else is supported`() {
        assertEquals(Platform.MAC_ARM64, Platform.current(osName = "Mac OS X", osArch = "aarch64"))
        assertEquals(Platform.MAC_AMD64, Platform.current(osName = "Mac OS X", osArch = "x86_64"))
        val error =
            assertThrows(IllegalStateException::class.java) { Platform.current(osName = "Linux", osArch = "amd64") }
        assertEquals("the startup tools run on macOS, not Linux on amd64", error.message)
    }

    private fun shell(version: String = "1.2") = TraceProcessorShell(
        log = PrintStream(log, true, Charsets.UTF_8),
        version = version,
        cacheDir = cacheDir,
        releasesUrl = "http://127.0.0.1:${server.address.port}",
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private companion object {
        val BINARY = "#!/bin/sh\necho stand-in trace processor\n".toByteArray()
        val LICENSE = "license".toByteArray()
    }
}
