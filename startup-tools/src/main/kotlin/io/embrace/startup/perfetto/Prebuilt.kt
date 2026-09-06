package io.embrace.startup.perfetto

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Duration

/**
 * Resolve, fetch and PIN the native Perfetto `trace_processor_shell`.
 *
 * Why a native prebuilt and not the upstream launcher: the launcher previously pinned as
 * "v46.0" is a Python script that itself downloads whichever engine its manifest names - v57.2 at
 * the time of the port. Two consequences the port fixes in one move: the toolchain no longer needs
 * `python3` to run its "binary", and the version recorded in every recipe (`trace_processor_version`)
 * now names the engine that actually produced the numbers. Bumping [VERSION] is a recipe change:
 * re-baseline longitudinal series and say so in the report.
 *
 * Resolution order (first hit wins), unchanged since the previous toolchain:
 * 1. an explicit path passed by the caller;
 * 2. `$STARTUP_TOOLS_DIR` (a pre-seeded or offline cache);
 * 3. `$XDG_CACHE_HOME/embrace-startup-tools`, else `~/.cache/embrace-startup-tools`;
 * 4. `<repo>/.claude/skills/.tools` (gitignored).
 *
 * Layout: `<cache>/trace_processor_shell/<version>/trace_processor_shell`, so pinned versions coexist
 * and a report can name exactly which one produced it. Before downloading, the launcher's own cache
 * (`~/.local/share/perfetto/prebuilts`) is checked for a file with the pinned digest - on a machine
 * that ran the previous toolchain the binary is already there.
 */
object Prebuilt {

    const val TOOL: String = "trace_processor_shell"
    const val VERSION: String = "v57.2"
    const val CACHE_DIR_NAME: String = "embrace-startup-tools"

    data class Platform(val key: String, val url: String, val sha256: String)

    /** Coordinates from the v57.2 launcher manifest. Only macOS is pinned; add a row to support another host. */
    val PLATFORMS: Map<String, Platform> = listOf(
        Platform(
            key = "mac-arm64",
            url = "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v57.2/mac-arm64/trace_processor_shell",
            sha256 = "98a41b80e9f60da0373d64aff6455681f8c26b7c391ae5736324a5b11e3dacc2",
        ),
        Platform(
            key = "mac-amd64",
            url = "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v57.2/mac-amd64/trace_processor_shell",
            sha256 = "c0f61397901da47cbe1bb9a0843624f7c2038ac92176ce15e3736ce9aa0afef0",
        ),
    ).associateBy { it.key }

    data class Provenance(val traceProcessorVersion: String, val traceProcessorPath: String?)

    /** What to record beside results so a later reader knows which engine produced them. */
    fun provenance(path: Path?): Provenance = Provenance(VERSION, path?.toString())

    /** The platform key for this host, or null when no prebuilt is pinned for it. */
    fun hostPlatform(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): String? {
        val mac = osName.lowercase().let { it.contains("mac") || it.contains("darwin") }
        if (!mac) return null
        return when (osArch.lowercase()) {
            "aarch64", "arm64" -> "mac-arm64"
            "x86_64", "amd64" -> "mac-amd64"
            else -> null
        }
    }

    /** Candidate cache roots in resolution order (see the class doc). */
    fun cacheDirs(
        env: Map<String, String> = System.getenv(),
        home: Path = Path.of(System.getProperty("user.home")),
        repoRoot: Path? = null,
    ): List<Path> {
        val dirs = ArrayList<Path>()
        env["STARTUP_TOOLS_DIR"]?.takeIf { it.isNotBlank() }?.let { dirs.add(expandHome(it, home)) }
        val xdg = env["XDG_CACHE_HOME"]?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) expandHome(xdg, home) else home.resolve(".cache")
        dirs.add(base.resolve(CACHE_DIR_NAME))
        repoRoot?.let { dirs.add(it.resolve(".claude").resolve("skills").resolve(".tools")) }
        return dirs
    }

    /**
     * A runnable `trace_processor_shell`. Explicit paths are trusted as given (they must exist);
     * otherwise the caches are searched, then the launcher's prebuilt cache is checked for a file with
     * the pinned digest, and only then is the prebuilt downloaded - once per machine.
     */
    fun resolve(
        explicit: Path? = null,
        env: Map<String, String> = System.getenv(),
        home: Path = Path.of(System.getProperty("user.home")),
        repoRoot: Path? = null,
        platformKey: String? = hostPlatform(),
        log: (String) -> Unit = { System.err.println(it) },
    ): Path {
        if (explicit != null) {
            if (!Files.exists(explicit)) throw IOException("trace_processor_shell not found at $explicit")
            return explicit
        }
        val dirs = cacheDirs(env, home, repoRoot)
        dirs.map { it.resolve(TOOL).resolve(VERSION).resolve(TOOL) }.firstOrNull { Files.isExecutable(it) }
            ?.let { return it }

        val platform = pinnedPlatform(platformKey)
        val target = firstWritable(dirs).resolve(TOOL).resolve(VERSION).resolve(TOOL)
        Files.createDirectories(target.parent)
        val partial = target.resolveSibling("$TOOL.partial")

        val seed = launcherCacheCandidate(home, platform.sha256)
        if (seed != null) {
            log("seeding $TOOL $VERSION from $seed (digest matches the pin)")
            Files.copy(seed, partial, StandardCopyOption.REPLACE_EXISTING)
        } else {
            log("fetching $TOOL $VERSION -> $target (once per machine)")
            download(platform.url, partial)
        }
        verifyDigest(partial, platform.sha256)
        makeExecutable(partial)
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target
    }

    private fun pinnedPlatform(platformKey: String?): Platform =
        PLATFORMS[platformKey] ?: throw IOException(
            "no $TOOL $VERSION prebuilt is pinned for this host ($platformKey); place one at " +
                "<cache>/$TOOL/$VERSION/$TOOL or set STARTUP_TOOLS_DIR to a cache that has it",
        )

    private fun verifyDigest(file: Path, expected: String) {
        val digest = sha256(file)
        if (digest != expected) {
            Files.deleteIfExists(file)
            throw IOException("downloaded $TOOL has sha256 $digest, expected $expected; refusing to use it")
        }
    }

    fun sha256(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun launcherCacheCandidate(home: Path, wantSha: String): Path? {
        val dir = home.resolve(".local").resolve("share").resolve("perfetto").resolve("prebuilts")
        if (!Files.isDirectory(dir)) return null
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().startsWith(TOOL) }
                .filter { runCatching { sha256(it) == wantSha }.getOrDefault(false) }
                .findFirst()
                .orElse(null)
        }
    }

    private fun download(url: String, into: Path) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S))
            .build()
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(READ_TIMEOUT_S)).GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofFile(into))
        } catch (e: IOException) {
            throw IOException(
                "could not download $TOOL ($e). Fetch $url manually to $into, or point STARTUP_TOOLS_DIR at a " +
                    "machine that already has it.",
                e,
            )
        }
        if (response.statusCode() != HTTP_OK) {
            Files.deleteIfExists(into)
            throw IOException("download of $url returned HTTP ${response.statusCode()}")
        }
    }

    private fun firstWritable(dirs: List<Path>): Path {
        dirs.forEach { dir ->
            val ok = runCatching {
                Files.createDirectories(dir)
                Files.isWritable(dir)
            }.getOrDefault(false)
            if (ok) return dir
        }
        throw IOException("no writable tools cache among $dirs; set STARTUP_TOOLS_DIR to a writable path")
    }

    private fun makeExecutable(file: Path) {
        runCatching {
            val perms = Files.getPosixFilePermissions(file).toMutableSet()
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            Files.setPosixFilePermissions(file, perms)
        }.onFailure { file.toFile().setExecutable(true) }
    }

    private fun expandHome(raw: String, home: Path): Path =
        if (raw == "~") home else if (raw.startsWith("~/")) home.resolve(raw.substring(2)) else Path.of(raw)

    private const val BUFFER_BYTES = 1 shl 16
    private const val CONNECT_TIMEOUT_S = 30L
    private const val READ_TIMEOUT_S = 600L
    private const val HTTP_OK = 200
}
