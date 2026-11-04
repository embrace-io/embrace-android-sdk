package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
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
    private val staging = noBackupFilesDir / SdkStorageLayout.MIGRATION_DIR_NAME
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)

    @Test
    fun `every legacy dir lands at its layout path intact`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)

        val migrator = migrator(fs)
        assertTrue(migrator.migrate())

        assertMigrated(fs, root, files)
        assertFalse(fs.exists(staging))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())

        migrator.deleteLeftovers()
        assertMigrated(fs, root, files)
    }

    @Test
    fun `the crash marker arrives without its prefix`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace" / "embrace_crash_marker", "marker")

        assertTrue(migrator(fs).migrate())

        val installState = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.INSTALL_STATE)
        assertEquals(listOf(installState / "crash_marker"), fs.list(installState))
        assertEquals("marker", fs.read(installState / "crash_marker") { readUtf8() })
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `install state prefixes are stripped without overwriting`() {
        val fs = fsRule.fileSystem
        val legacy = filesDir / "embrace"
        mapOf("emb_a" to "a", "embrace_b" to "prefixed", "b" to "plain", "c" to "c").forEach { (name, text) ->
            fs.writeText(legacy / name, text)
        }

        assertTrue(migrator(fs).migrate())

        val installState = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.INSTALL_STATE)
        val contents = fs.list(installState).associate { it.name to fs.read(it) { readUtf8() } }
        assertEquals(mapOf("a" to "a", "b" to "plain", "embrace_b" to "prefixed", "c" to "c"), contents)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `telemetry names keep their prefix`() {
        val fs = fsRule.fileSystem
        fs.writeText(filesDir / "embrace_payloads" / "embrace_x", "x")

        assertTrue(migrator(fs).migrate())

        val ready = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_READY)
        assertEquals(listOf(ready / "embrace_x"), fs.list(ready))
    }

    @Test
    fun `a legacy dir written after staging merges in on resume`() {
        val fs = fsRule.fileSystem
        val staged = SdkStorageImpl(fs, staging, errorHandler).path(SdkDir.TELEMETRY_READY)
        fs.writeText(staged / "old", "staged")
        fs.writeText(staged / "both", "staged")
        fs.writeText(filesDir / "embrace_payloads" / "new", "legacy")
        fs.writeText(filesDir / "embrace_payloads" / "both", "legacy")
        val migrator = migrator(fs)

        assertTrue(migrator.migrate())
        migrator.deleteLeftovers()

        val ready = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_READY)
        val contents = fs.list(ready).associate { it.name to fs.read(it) { readUtf8() } }
        assertEquals(mapOf("both" to "staged", "new" to "legacy", "old" to "staged"), contents)
        assertFalse(fs.exists(filesDir / "embrace_payloads"))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `install state that fails to move is dropped so identity resets`() {
        val blocked = filesDir / "embrace"
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (source == blocked) throw IOException("rename failed")
                super.atomicMove(source, target)
            }
        }
        val files = seedLegacyLayout(fs, filesDir)
        val migrator = migrator(fs)

        assertTrue(migrator.migrate())
        migrator.deleteLeftovers()

        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertMigrated(fs, root, files.filterKeys { it != SdkDir.INSTALL_STATE })
        assertFalse(fs.exists(SdkStorageImpl(fs, root, errorHandler).path(SdkDir.INSTALL_STATE)))
        assertFalse(fs.exists(blocked))
    }

    @Test
    fun `a failure checking the root is tracked`() {
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path == root) throw SecurityException("denied")
                return super.metadataOrNull(path)
            }
        }
        seedLegacyLayout(fs, filesDir)

        assertFalse(migrator(fs).migrate())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertFalse(fs.exists(staging))
    }

    @Test
    fun `a prefix that fails to strip is tracked and the rest committed`() {
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (source.name == "embrace_a") throw IOException("rename failed")
                super.atomicMove(source, target)
            }
        }
        fs.writeText(filesDir / "embrace" / "embrace_a", "a")
        fs.writeText(filesDir / "embrace" / "embrace_b", "b")

        assertTrue(migrator(fs).migrate())

        val installState = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.INSTALL_STATE)
        assertEquals(setOf("embrace_a", "b"), fs.list(installState).map { it.name }.toSet())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a merge carries on past a child that fails to move`() {
        val blocked = filesDir / "embrace_payloads" / "b"
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (source == blocked) throw IOException("rename failed")
                super.atomicMove(source, target)
            }
        }
        val ready = SdkStorageImpl(fs, staging, errorHandler).path(SdkDir.TELEMETRY_READY)
        fs.writeText(ready / "staged", "staged")
        listOf("a", "b", "c").forEach { fs.writeText(filesDir / "embrace_payloads" / it, it) }

        assertTrue(migrator(fs).migrate())

        val committed = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_READY)
        assertEquals(setOf("a", "c", "staged"), fs.list(committed).map { it.name }.toSet())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `a merge recurses into dirs present on both sides`() {
        val fs = fsRule.fileSystem
        val parts = SdkStorageImpl(fs, staging, errorHandler).path(SdkDir.TELEMETRY_PENDING_SESSION_PARTS)
        fs.writeText(parts / "p1" / "x", "staged")
        fs.writeText(parts / "p1" / "y", "staged")
        val legacy = filesDir / "embrace_sessions_split"
        fs.writeText(legacy / "p1" / "y", "legacy")
        fs.writeText(legacy / "p1" / "z", "legacy")
        fs.writeText(legacy / "p2" / "w", "legacy")

        assertTrue(migrator(fs).migrate())

        val committed = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_PENDING_SESSION_PARTS)
        val contents = fs.listRecursively(committed).filter { fs.metadata(it).isRegularFile }
            .associate { it.relativeTo(committed).toString() to fs.read(it) { readUtf8() } }
        assertEquals(mapOf("p1/x" to "staged", "p1/y" to "staged", "p1/z" to "legacy", "p2/w" to "legacy"), contents)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `install state that can't be listed aborts the commit`() {
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            var failList = true
            override fun list(dir: Path): List<Path> {
                if (failList && dir.name == SdkDir.INSTALL_STATE.dirName) throw IOException("unreadable")
                return super.list(dir)
            }
        }
        fs.writeText(filesDir / "embrace" / "embrace_crash_marker", "marker")

        assertFalse(migrator(fs).migrate())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertFalse(fs.exists(root))

        fs.failList = false
        assertTrue(migrator(fs).migrate())
        val installState = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.INSTALL_STATE)
        assertEquals(listOf(installState / "crash_marker"), fs.list(installState))
    }

    @Test
    fun `a half staged run resumes`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)
        val layout = SdkStorageImpl(fs, staging, errorHandler)
        listOf(SdkDir.INSTALL_STATE, SdkDir.TELEMETRY_PENDING_SESSION_PARTS).forEach { dir ->
            val target = layout.path(dir)
            fs.createDirectories(checkNotNull(target.parent))
            fs.atomicMove(filesDir / LegacySdkStorage.LEGACY_DIR_NAMES.getValue(dir), target)
        }

        assertTrue(migrator(fs).migrate())

        assertMigrated(fs, root, files)
        assertFalse(fs.exists(staging))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a second run does nothing`() {
        val fs = fsRule.fileSystem
        val files = seedLegacyLayout(fs, filesDir)
        assertTrue(migrator(fs).migrate())
        val payload = (filesDir / "embrace_payloads" / "late").also { fs.writeText(it, "late") }

        assertFalse(migrator(fs).migrate())

        assertMigrated(fs, root, files, legacyDir = null)
        assertTrue(fs.exists(payload))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a fresh install does nothing`() {
        val fs = fsRule.fileSystem

        assertFalse(migrator(fs).migrate())

        assertFalse(fs.exists(root))
        assertFalse(fs.exists(staging))
        assertFalse(fs.exists(filesDir))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a fully staged run with no legacy dirs left still commits`() {
        val fs = fsRule.fileSystem
        val ready = SdkStorageImpl(fs, staging, errorHandler).path(SdkDir.TELEMETRY_READY)
        fs.writeText(ready / "payload", "staged")

        assertTrue(migrator(fs).migrate())

        val committed = SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_READY)
        assertEquals("staged", fs.read(committed / "payload") { readUtf8() })
        assertFalse(fs.exists(staging))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `a dir that fails to move is dropped and the rest committed`() {
        val blocked = filesDir / "embrace_native"
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (source == blocked) throw IOException("rename failed")
                super.atomicMove(source, target)
            }
        }
        val files = seedLegacyLayout(fs, filesDir)
        val migrator = migrator(fs)

        assertTrue(migrator.migrate())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertMigrated(fs, root, files.filterKeys { it != SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES })
        assertFalse(fs.exists(SdkStorageImpl(fs, root, errorHandler).path(SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES)))

        migrator.deleteLeftovers()
        assertFalse(fs.exists(blocked))
    }

    @Test
    fun `a failed commit leaves staging to resume from`() {
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            var failCommit = true
            override fun atomicMove(source: Path, target: Path) {
                if (source == staging && failCommit) throw IOException("rename failed")
                super.atomicMove(source, target)
            }
        }
        val files = seedLegacyLayout(fs, filesDir)

        assertFalse(migrator(fs).migrate())
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        assertFalse(fs.exists(root))

        fs.failCommit = false
        assertTrue(migrator(fs).migrate())
        assertMigrated(fs, root, files)
    }

    @Test
    fun `deleteLeftovers removes 9x cache dir entries and keeps the app's files`() {
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
        val migrator = migrator(fs)
        migrator.migrate()

        migrator.deleteLeftovers()

        leftovers.forEach { assertFalse(fs.exists(it)) }
        appFiles.forEach { assertTrue(fs.exists(it)) }
        assertEquals(setOf("embrace_app_owned"), fs.list(cacheDir).map { it.name }.toSet())
    }

    @Test
    fun `deleteLeftovers still cleans the files dir if the cache dir can't be resolved`() {
        val fs = fsRule.fileSystem
        val leftover = (filesDir / "embrace_payloads" / "payload").also { fs.writeText(it, "x") }
        val migrator = LegacyStorageMigrator(fs, filesDir, { error("no cache dir") }, noBackupFilesDir, errorHandler)

        migrator.deleteLeftovers()

        assertFalse(fs.exists(leftover))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `directory renames behave the same on disk`() {
        val fs = FileSystem.SYSTEM
        val disk = tmpFolder.root.toOkioPath()
        val files = seedLegacyLayout(fs, disk / "files")
        val migrator = LegacyStorageMigrator(fs, disk / "files", { disk / "cache" }, disk / "no_backup", errorHandler)

        assertTrue(migrator.migrate())

        assertMigrated(fs, disk / "no_backup" / SdkStorageLayout.ROOT_DIR_NAME, files, disk / "files")
        assertFalse(fs.exists(disk / "no_backup" / SdkStorageLayout.MIGRATION_DIR_NAME))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    private fun migrator(fs: FileSystem) = LegacyStorageMigrator(fs, filesDir, { cacheDir }, noBackupFilesDir, errorHandler)

    /**
     * Writes a file into every legacy dir and returns the relative path of each file, keyed by its dir.
     */
    private fun seedLegacyLayout(fs: FileSystem, filesDir: Path): Map<SdkDir, String> =
        LegacySdkStorage.LEGACY_DIR_NAMES.mapValues { (dir, name) ->
            val file = when (dir) {
                SdkDir.INSTALL_STATE -> "embrace_crash_marker"
                SdkDir.TELEMETRY_PENDING_SESSION_PARTS -> "part-id/span"
                else -> "file-$name"
            }
            fs.writeText(filesDir / name / file, name)
            file
        }

    private fun assertMigrated(fs: FileSystem, root: Path, files: Map<SdkDir, String>, legacyDir: Path? = filesDir) {
        val layout = SdkStorageImpl(fs, root, errorHandler)
        files.forEach { (dir, file) ->
            val name = LegacySdkStorage.LEGACY_DIR_NAMES.getValue(dir)
            val migrated = layout.path(dir) / file.removePrefix("embrace_")
            assertEquals(name, fs.read(migrated) { readUtf8() })
            legacyDir?.let { assertFalse(fs.exists(it / name)) }
        }
    }

    private fun FileSystem.writeText(path: Path, text: String) {
        createDirectories(checkNotNull(path.parent))
        write(path) { writeUtf8(text) }
    }
}
