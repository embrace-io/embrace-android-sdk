package io.embrace.analysis.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PrebuiltTest {

    private val home: Path = Files.createTempDirectory("startup-tools-home")

    @Test
    fun `cache directories follow the tooling py resolution order`() {
        val repo = Files.createTempDirectory("repo")
        val dirs = Prebuilt.cacheDirs(
            env = mapOf("STARTUP_TOOLS_DIR" to "~/tools", "XDG_CACHE_HOME" to "/xdg"),
            home = home,
            repoRoot = repo,
        )
        assertEquals(
            listOf(
                home.resolve("tools"),
                Path.of("/xdg").resolve(Prebuilt.CACHE_DIR_NAME),
                repo.resolve(".claude/skills/.tools"),
            ),
            dirs,
        )
        val defaults = Prebuilt.cacheDirs(env = emptyMap(), home = home)
        assertEquals(listOf(home.resolve(".cache").resolve(Prebuilt.CACHE_DIR_NAME)), defaults)
    }

    @Test
    fun `host platform maps mac architectures and nothing else`() {
        assertEquals("mac-arm64", Prebuilt.hostPlatform("Mac OS X", "aarch64"))
        assertEquals("mac-amd64", Prebuilt.hostPlatform("Mac OS X", "x86_64"))
        assertNull(Prebuilt.hostPlatform("Linux", "amd64"))
        assertTrue(Prebuilt.PLATFORMS.keys.containsAll(listOf("mac-arm64", "mac-amd64")))
    }

    @Test
    fun `sha256 matches the well-known digest`() {
        val f = Files.createTempFile(home, "abc", ".txt")
        Files.writeString(f, "abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Prebuilt.sha256(f))
    }

    @Test
    fun `a pre-seeded cache is used without touching the network, and an explicit missing path is refused`() {
        val cache = home.resolve("seeded")
        val binary = cache.resolve(Prebuilt.TOOL).resolve(Prebuilt.VERSION).resolve(Prebuilt.TOOL)
        Files.createDirectories(binary.parent)
        Files.writeString(binary, "#!/bin/sh\nexit 0\n")
        binary.toFile().setExecutable(true)
        val resolved = Prebuilt.resolve(
            env = mapOf("STARTUP_TOOLS_DIR" to cache.toString()),
            home = home,
            platformKey = "mac-arm64",
            log = {},
        )
        assertEquals(binary, resolved)

        val missing = runCatching { Prebuilt.resolve(explicit = home.resolve("nope")) }
        assertTrue(missing.exceptionOrNull() is IOException)

        val unpinned = runCatching {
            Prebuilt.resolve(
                env = mapOf("STARTUP_TOOLS_DIR" to home.resolve("empty").toString()),
                home = home,
                platformKey = "linux-amd64",
                log = {},
            )
        }
        assertTrue(unpinned.exceptionOrNull()?.message?.contains("no trace_processor_shell v57.2 prebuilt") == true)
    }
}
