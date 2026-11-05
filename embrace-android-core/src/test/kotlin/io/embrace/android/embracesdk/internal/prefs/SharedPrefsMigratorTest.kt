package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSharedPreferences
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.ALL_LIVE_KEYS
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.IDENTITY
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.USER_SESSION
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class SharedPrefsMigratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fileSystem = FileSystem.SYSTEM
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val worker = BackgroundWorker(BlockingScheduledExecutorService(blockingMode = true))

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var installStateDir: Path
    private lateinit var prefsFile: Path
    private var prefsLoaded = false

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        val root = tmp.root.toOkioPath()
        installStateDir = root / "install_state"
        prefsFile = root / "shared_prefs" / "pkg_preferences.xml"
        fileSystem.createDirectories(installStateDir)
    }

    @Test
    fun `every value lands in the right store`() {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate())
        assertMigrated()
    }

    private fun assertMigrated() {
        assertEquals(
            PersistedSdkState(
                deviceId = "device-id",
                installTimestampMs = 99L,
                userId = "user-id",
                userEmail = "user@example.com",
                userName = "user-name",
                userPersonas = setOf("persona"),
                sessionProperties = mapOf("prop" to "value"),
                userSession = USER_SESSION,
                rnBundleUrl = "assets://index.android.bundle",
                rnBundleId = "bundle-id",
                appVersionStartupScope = "1.0_1",
            ),
            persistedState(),
        )
        val ordinals = openOrdinals()
        assertEquals(42, ordinals.incrementAndGet(Ordinal.SESSION) { 1 })
        assertEquals(51, ordinals.incrementAndGet(Ordinal.SESSION_PART) { 1 })
        assertEquals(3, ordinals.incrementAndGet(Ordinal.CRASH) { 1 })
        assertEquals(4, ordinals.incrementAndGet(Ordinal.NATIVE_CRASH) { 1 })
        assertEquals(5, ordinals.incrementAndGet(Ordinal.AEI_CRASH) { 1 })
        assertEquals(8, ordinals.incrementAndGet(Ordinal.APP_VERSION_STARTUP) { 1 })
    }

    @Test
    fun `each framework's hosted sdk version keys land in the typed fields`() {
        prefs.seed(
            *IDENTITY,
            "io.embrace.reactnative.sdk.version" to "rn-sdk",
            "io.embrace.reactnative.version" to "rn",
            "io.embrace.javascript.patch" to "js-patch",
            "io.embrace.unity.sdk.version" to "unity-sdk",
        )
        assertTrue(migrate(AppFramework.REACT_NATIVE))
        val state = persistedState()
        assertEquals("rn-sdk", state.hostedSdkVersion)
        assertEquals("rn", state.hostedPlatformVersion)
        assertEquals("js-patch", state.javaScriptPatchNumber)
        assertNull(state.unityBuildIdNumber)
    }

    @Test
    fun `no persisted key contains embrace`() {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate())
        fileSystem.list(installStateDir).forEach { file ->
            val bytes = fileSystem.read(file) { readByteString() }
            assertEquals(file.name, -1, bytes.indexOf("embrace".encodeUtf8()))
        }
    }

    @Test
    fun `fresh install writes empty ordinals without loading prefs`() {
        assertTrue(migrate(createPrefsFile = false))
        assertFalse(prefsLoaded)
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
    }

    @Test
    fun `prefs without identity write empty ordinals`() {
        prefs.seed("io.embrace.sessionnumber" to 41, "my.app.counter" to 3)
        assertTrue(migrate())
        assertTrue(prefsLoaded)
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
    }

    @Test
    fun `wrong-typed values migrate as absent`() {
        prefs.seed(
            *IDENTITY,
            "io.embrace.userid" to 1,
            "io.embrace.session.properties" to "{not json",
            "io.embrace.sessionnumber" to "41",
        )
        assertTrue(migrate())
        val state = persistedState()
        assertEquals("device-id", state.deviceId)
        assertNull(state.userId)
        assertTrue(state.sessionProperties.isEmpty())
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
    }

    @Test
    fun `existing ordinals file skips the migration`() {
        prefs.seed(*ALL_LIVE_KEYS)
        MmapOrdinalStore.create(fileSystem, ordinalsPath(), emptyMap())
        assertFalse(migrate())
        assertFalse(prefsLoaded)
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
    }

    @Test
    fun `failure writing ordinals leaves prefs intact and reruns to the same result`() {
        prefs.seed(*ALL_LIVE_KEYS)
        val failing = object : ForwardingFileSystem(fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == ordinalsPath()) {
                    throw IOException("disk full")
                }
                super.atomicMove(source, target)
            }
        }
        assertFalse(migrate(fs = failing))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFalse(fileSystem.exists(ordinalsPath()))
        assertTrue(fileSystem.exists(installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME))
        assertEquals(ALL_LIVE_KEYS.size, prefs.all.size)

        assertTrue(migrate())
        assertMigrated()
    }

    private fun migrate(
        appFramework: AppFramework = AppFramework.NATIVE,
        fs: FileSystem = fileSystem,
        createPrefsFile: Boolean = true,
    ): Boolean {
        if (createPrefsFile) {
            fileSystem.createDirectories(checkNotNull(prefsFile.parent))
            fileSystem.write(prefsFile) { writeUtf8("<map />") }
        }
        return SharedPrefsMigrator(
            fileSystem = fs,
            installStateDir = installStateDir,
            prefsFile = prefsFile,
            loadPrefs = {
                prefsLoaded = true
                prefs
            },
            serializer = TestPlatformSerializer(),
            errorHandler = errorHandler,
            appFramework = appFramework,
        ).migrate()
    }

    private fun ordinalsPath(): Path = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME

    private fun openOrdinals(): MmapOrdinalStore = checkNotNull(MmapOrdinalStore.open(ordinalsPath().toFile(), errorHandler))

    private fun persistedState(): PersistedSdkState = FilePersistedSdkStateStore(
        fileSystem = fileSystem,
        path = installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
        worker = worker,
        errorHandler = errorHandler,
        clock = { -1L },
        uuidSource = { "generated" },
    ).snapshot().also { assertNotEquals("generated", it.deviceId) }
}
