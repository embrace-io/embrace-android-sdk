package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class LegacyStorageMigratorTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val data = "/data".toPath()
    private val filesDir = data / "files"
    private val cacheDir = data / "cache"
    private val noBackupFilesDir = data / "no_backup"
    private val root = noBackupFilesDir / SdkStorageLayout.ROOT_DIR_NAME
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)

    @Test
    fun `migrateConfig moves only the remote config`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)

        migrator(fs).migrateConfig()

        assertMigrated(fs, root, files.filterKeys { it == SdkDir.REMOTE_CONFIG })
        val untouched = LegacyStorageMigrator.LEGACY_DIR_NAMES - SdkDir.REMOTE_CONFIG
        untouched.values.forEach { assertTrue("$it moved", fs.exists(filesDir / it)) }
        assertEquals(listOf(layout(fs).path(SdkDir.REMOTE_CONFIG)), fs.list(root))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `migrateConfig on a fresh install does nothing`() {
        val fs = fsRule.fileSystem

        migrateAll(migrator(fs))

        assertFalse(fs.exists(root))
        assertFalse(fs.exists(filesDir))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `hasLegacyConfig is true only while 9x config is waiting to move`() {
        val fs = fsRule.fileSystem
        val migrator = migrator(fs)
        assertFalse(migrator.hasLegacyConfig())

        fs.writeText(filesDir / "embrace_remote_config" / "most_recent_response", "legacy")
        assertTrue(migrator.hasLegacyConfig())

        migrator.migrateConfig()
        assertFalse(migrator.hasLegacyConfig())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `hasLegacyConfig is true if the files dir can't be resolved`() {
        val fs = fsRule.fileSystem
        val migrator =
            LegacyStorageMigrator(fs, { error("no files dir") }, { cacheDir }, { noBackupFilesDir }, errorHandler)

        assertTrue(migrator.hasLegacyConfig())
    }

    @Test
    fun `migrateConfig keeps config already in the root`() {
        val fs = fsRule.fileSystem
        val config = layout(fs).path(SdkDir.REMOTE_CONFIG)
        fs.writeText(config / "most_recent_response", "live")
        fs.writeText(filesDir / "embrace_remote_config" / "most_recent_response", "legacy")
        fs.writeText(filesDir / "embrace_remote_config" / "etag", "legacy")

        migrator(fs).migrateConfig()

        assertEquals(mapOf("etag" to "legacy", "most_recent_response" to "live"), contents(fs, config))
        assertFalse(fs.exists(filesDir / "embrace_remote_config"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `config that fails to move is kept and moved by moveTelemetry`() {
        val blocked = filesDir / "embrace_remote_config"
        val fs = BlockingFileSystem(fsRule.fileSystem, blocked)
        fs.writeText(blocked / "most_recent_response", "legacy")
        val migrator = migrator(fs)

        migrator.migrateConfig()
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertTrue(fs.exists(blocked / "most_recent_response"))

        fs.blocked = null
        migrator.moveTelemetry()
        val config = layout(fs).path(SdkDir.REMOTE_CONFIG)
        assertEquals(mapOf("most_recent_response" to "legacy"), contents(fs, config))
        assertFalse(fs.exists(blocked))
    }

    @Test
    fun `an abandoned config isn't moved`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace_remote_config" / "etag", "legacy")
        val migrator = migrator(fs)

        migrator.abandonConfig()
        migrator.migrateConfig()

        assertFalse(fs.exists(layout(fs).path(SdkDir.REMOTE_CONFIG)))
        assertTrue(fs.exists(filesDir / "embrace_remote_config" / "etag"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `migrateTelemetry deletes an abandoned config instead of merging it`() {
        val fs = fsRule.fileSystem
        val config = layout(fs).path(SdkDir.REMOTE_CONFIG)
        fs.writeText(config / "etag", "fresh")
        fs.writeText(config / "most_recent_response", "fresh")
        fs.writeText(filesDir / "embrace_remote_config" / "etag", "legacy")
        fs.writeText(filesDir / "embrace_remote_config" / "cached_config", "legacy")
        fs.writeText(filesDir / "embrace_payloads" / "p", "legacy")
        val migrator = migrator(fs)

        migrator.abandonConfig()
        migrator.migrateTelemetry()

        assertEquals(mapOf("etag" to "fresh", "most_recent_response" to "fresh"), contents(fs, config))
        assertFalse(fs.exists(filesDir / "embrace_remote_config"))
        assertEquals(mapOf("p" to "legacy"), contents(fs, layout(fs).path(SdkDir.TELEMETRY_READY)))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `every telemetry dir lands at its layout path intact`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)

        migrateAll(migrator(fs))

        assertMigrated(fs, root, files - SdkDir.INSTALL_STATE)
        assertFalse(fs.exists(filesDir / "embrace"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `the legacy crash marker is moved and the rest of the install state deleted`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace" / "embrace_crash_marker", "legacy")
        fs.writeText(filesDir / "embrace" / "other", "x")

        migrateAll(migrator(fs))

        val installState = layout(fs).path(SdkDir.INSTALL_STATE)
        assertEquals(mapOf(SdkStorageLayout.CRASH_MARKER_FILE_NAME to "legacy"), contents(fs, installState))
        assertFalse(fs.exists(filesDir / "embrace"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a crash marker the SDK already wrote is kept`() {
        val fs = fsRule.fileSystem
        val marker = layout(fs).path(SdkDir.INSTALL_STATE) / SdkStorageLayout.CRASH_MARKER_FILE_NAME
        fs.writeText(marker, "live")
        fs.writeText(filesDir / "embrace" / "embrace_crash_marker", "legacy")

        migrator(fs).migrateTelemetry()

        assertEquals("live", fs.read(marker) { readUtf8() })
        assertFalse(fs.exists(filesDir / "embrace"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `telemetry merges into dirs the SDK already wrote without overwriting`() {
        val fs = fsRule.fileSystem
        val ready = layout(fs).path(SdkDir.TELEMETRY_READY)
        fs.writeText(ready / "new", "live")
        fs.writeText(ready / "both", "live")
        fs.writeText(filesDir / "embrace_payloads" / "old", "legacy")
        fs.writeText(filesDir / "embrace_payloads" / "both", "legacy")

        migrator(fs).migrateTelemetry()

        assertEquals(mapOf("both" to "live", "new" to "live", "old" to "legacy"), contents(fs, ready))
        assertFalse(fs.exists(filesDir / "embrace_payloads"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a merge recurses into dirs present on both sides`() {
        val fs = fsRule.fileSystem
        val parts = layout(fs).path(SdkDir.TELEMETRY_PENDING_SESSION_PARTS)
        fs.writeText(parts / "p1" / "x", "live")
        fs.writeText(parts / "p1" / "y", "live")
        val legacy = filesDir / "embrace_sessions_split"
        fs.writeText(legacy / "p1" / "y", "legacy")
        fs.writeText(legacy / "p1" / "z", "legacy")
        fs.writeText(legacy / "p2" / "w", "legacy")

        migrator(fs).migrateTelemetry()

        val merged = fs.listRecursively(parts).filter { fs.metadata(it).isRegularFile }
            .associate { it.relativeTo(parts).toString() to fs.read(it) { readUtf8() } }
        assertEquals(mapOf("p1/x" to "live", "p1/y" to "live", "p1/z" to "legacy", "p2/w" to "legacy"), merged)
        assertFalse(fs.exists(legacy))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `an entry that fails to move is deleted rather than retried`() {
        val legacy = filesDir / "embrace_payloads"
        val fs = BlockingFileSystem(fsRule.fileSystem, legacy / "b")
        val ready = layout(fs).path(SdkDir.TELEMETRY_READY)
        fs.writeText(ready / "live", "live")
        listOf("a", "b", "c").forEach { fs.writeText(legacy / it, it) }

        migrator(fs).migrateTelemetry()

        assertEquals(setOf("a", "c", "live"), fs.list(ready).map { it.name }.toSet())
        assertFalse(fs.exists(legacy))
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a dir that fails to move doesn't stop the others and is deleted with the install state`() {
        val blocked = filesDir / "embrace_native"
        val fs = BlockingFileSystem(fsRule.fileSystem, blocked)
        val files = seedLegacyLayout(fs, filesDir)
        val migrator = migrator(fs)

        migrateAll(migrator)

        assertMigrated(fs, root, files - SdkDir.INSTALL_STATE - SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES)
        assertFalse(fs.exists(blocked))
        assertFalse(migrator.hasLegacyTelemetry())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `deleteLegacyInstallState deletes the 9x cache dir entries and keeps the app's files`() {
        val fs = fsRule.fileSystem
        val leftovers = listOf(
            cacheDir / "embrace_payloads" / "payload",
            cacheDir / "embrace_sessions_split" / "part",
            cacheDir / "embrace_crash_marker",
            cacheDir / "emb_config_cache" / "etag",
        ).onEach { fs.writeText(it, "x") }
        val appFiles = listOf(
            cacheDir / "embrace_app_owned",
            filesDir / "app-file",
        ).onEach { fs.writeText(it, "x") }

        migrator(fs).migrateTelemetry()

        leftovers.forEach { assertFalse(fs.exists(it)) }
        appFiles.forEach { assertTrue(fs.exists(it)) }
        assertEquals(setOf("embrace_app_owned"), fs.list(cacheDir).map { it.name }.toSet())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `telemetry still migrates if the cache dir can't be resolved`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace_payloads" / "payload", "x")
        val migrator =
            LegacyStorageMigrator(fs, { filesDir }, { error("no cache dir") }, { noBackupFilesDir }, errorHandler)

        migrator.migrateTelemetry()

        assertEquals("x", fs.read(layout(fs).path(SdkDir.TELEMETRY_READY) / "payload") { readUtf8() })
        assertFalse(fs.exists(filesDir / "embrace_payloads"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `moveTelemetry leaves the rest of the install state and the cache dir entries`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace" / "embrace_crash_marker", "legacy")
        fs.writeText(filesDir / "embrace" / "other", "x")
        fs.writeText(filesDir / "embrace_payloads" / "payload", "x")
        val cached = (cacheDir / "embrace_payloads" / "payload").also { fs.writeText(it, "x") }
        val migrator = migrator(fs)

        migrator.moveTelemetry()

        assertTrue(fs.exists(layout(fs).path(SdkDir.TELEMETRY_READY) / "payload"))
        assertTrue(fs.exists(layout(fs).path(SdkDir.INSTALL_STATE) / SdkStorageLayout.CRASH_MARKER_FILE_NAME))
        assertTrue(fs.exists(filesDir / "embrace" / "other"))
        assertTrue(fs.exists(cached))

        migrator.deleteLegacyInstallState()

        assertFalse(fs.exists(filesDir / "embrace"))
        assertFalse(fs.exists(cached))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `deleteLegacyTelemetry deletes 9x telemetry without moving it`() {
        val fs = fsRule.fileSystem
        seedLegacyLayout(fs, filesDir)
        val cached = (cacheDir / "embrace_payloads" / "payload").also { fs.writeText(it, "x") }

        migrator(fs).deleteLegacyTelemetry()

        LegacyStorageMigrator.LEGACY_DIR_NAMES.forEach { (dir, name) ->
            assertEquals(name, !dir.isTelemetry, fs.exists(filesDir / name))
        }
        assertFalse(fs.exists(cached))
        assertFalse(fs.exists(root))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a second run does nothing`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)
        migrateAll(migrator(fs))

        migrateAll(migrator(fs))

        assertMigrated(fs, root, files - SdkDir.INSTALL_STATE)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `hasLegacyTelemetry is true until the 9x install state is deleted`() {
        val fs = fsRule.fileSystem
        val migrator = migrator(fs)
        assertFalse(migrator.hasLegacyTelemetry())
        fs.writeText(filesDir / "embrace_payloads" / "payload", "x")
        assertFalse(migrator.hasLegacyTelemetry())

        seedLegacyLayout(fs, filesDir)
        assertTrue(migrator.hasLegacyTelemetry())

        migrateAll(migrator)
        assertFalse(migrator.hasLegacyTelemetry())
    }

    @Test
    fun `hasLegacyTelemetry is true if the files dir can't be resolved`() {
        val migrator = LegacyStorageMigrator(
            fsRule.fileSystem,
            { error("no files dir") },
            { cacheDir },
            { noBackupFilesDir },
            errorHandler,
        )

        assertTrue(migrator.hasLegacyTelemetry())
    }

    @Test
    fun `every dir with a 9x location maps to its 9x name`() {
        val expected = mapOf(
            SdkDir.REMOTE_CONFIG to "embrace_remote_config",
            SdkDir.INSTALL_STATE to "embrace",
            SdkDir.TELEMETRY_READY to "embrace_payloads",
            SdkDir.TELEMETRY_PENDING_SNAPSHOTS to "embrace_cache",
            SdkDir.TELEMETRY_PENDING_SESSION_PARTS to "embrace_sessions_split",
            SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES to "embrace_native",
            SdkDir.TELEMETRY_LOG_ENVELOPES to "embrace_envelopes",
        )
        assertEquals(expected, LegacyStorageMigrator.LEGACY_DIR_NAMES)
    }

    @Test
    fun `directory renames behave the same on disk`() {
        val fs = FileSystem.SYSTEM
        val disk = tmpFolder.root.toOkioPath()
        val files = seedLegacyLayout(fs, disk / "files")
        val migrator =
            LegacyStorageMigrator(fs, { disk / "files" }, { disk / "cache" }, { disk / "no_backup" }, errorHandler)

        migrateAll(migrator)

        assertMigrated(fs, disk / "no_backup" / SdkStorageLayout.ROOT_DIR_NAME, files - SdkDir.INSTALL_STATE, disk / "files")
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `deletes don't throw if the files dir can't be resolved`() {
        val fs = fsRule.fileSystem
        fs.writeText(cacheDir / "embrace_payloads" / "payload", "x")
        val migrator =
            LegacyStorageMigrator(fs, { error("no files dir") }, { cacheDir }, { noBackupFilesDir }, errorHandler)

        migrator.deleteLegacyTelemetry()
        assertFalse(fs.exists(cacheDir / "embrace_payloads"))

        fs.writeText(cacheDir / "emb_config_cache" / "etag", "x")
        migrator.deleteLegacyInstallState()
        assertFalse(fs.exists(cacheDir / "emb_config_cache"))
    }

    @Test
    fun `creating the migrator doesn't resolve its dirs`() {
        LegacyStorageMigrator(
            fsRule.fileSystem,
            { error("files dir") },
            { error("cache dir") },
            { error("no backup dir") },
            errorHandler,
        )
    }

    private fun migrator(fs: FileSystem) =
        LegacyStorageMigrator(fs, { filesDir }, { cacheDir }, { noBackupFilesDir }, errorHandler)

    private fun layout(fs: FileSystem) = SdkStorageImpl(fs, root, errorHandler)

    private fun migrateAll(migrator: LegacyStorageMigrator) {
        migrator.migrateConfig()
        migrator.migrateTelemetry()
    }

    private fun LegacyStorageMigrator.migrateTelemetry() {
        moveTelemetry()
        deleteLegacyInstallState()
    }

    /**
     * Writes a file into every legacy dir and returns the relative path of each file, keyed by its dir.
     */
    private fun seedLegacyLayout(fs: FileSystem, filesDir: Path): Map<SdkDir, String> =
        LegacyStorageMigrator.LEGACY_DIR_NAMES.mapValues { (dir, name) ->
            val file = when (dir) {
                SdkDir.INSTALL_STATE -> "embrace_crash_marker"
                SdkDir.TELEMETRY_PENDING_SESSION_PARTS -> "part-id/span"
                else -> "file-$name"
            }
            fs.writeText(filesDir / name / file, name)
            file
        }

    private fun assertMigrated(fs: FileSystem, root: Path, files: Map<SdkDir, String>, legacyDir: Path = filesDir) {
        val layout = SdkStorageImpl(fs, root, errorHandler)
        files.forEach { (dir, file) ->
            val name = LegacyStorageMigrator.LEGACY_DIR_NAMES.getValue(dir)
            assertEquals(name, fs.read(layout.path(dir) / file) { readUtf8() })
            assertFalse(fs.exists(legacyDir / name))
        }
    }

    private fun contents(fs: FileSystem, dir: Path): Map<String, String> =
        fs.list(dir).associate { it.name to fs.read(it) { readUtf8() } }

    private fun FileSystem.writeText(path: Path, text: String) {
        createDirectories(checkNotNull(path.parent))
        write(path) { writeUtf8(text) }
    }
}
