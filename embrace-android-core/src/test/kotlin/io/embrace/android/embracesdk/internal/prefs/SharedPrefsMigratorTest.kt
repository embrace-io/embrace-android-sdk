package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
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
import io.embrace.android.embracesdk.internal.store.file.FileDeliveredAeiStore
import io.embrace.android.embracesdk.internal.store.file.FileNetworkCaptureRuleStore
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
    private var liveRules = mapOf("1" to 10, "rule-b" to 3, "unused" to 4)
    private var liveRulesProvider: () -> Map<String, Int> = { liveRules }

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

        val rules = networkCaptureStore()
        assertEquals(5, rules.remaining("1", 10))
        assertEquals(0, rules.remaining("rule-b", 3))
        assertEquals(4, rules.remaining("unused", 4))
        assertEquals(setOf("100_1", "200_2"), deliveredAeiStore().ids())
        assertEquals(
            setOf(
                SdkStorageLayout.ORDINALS_FILE_NAME,
                SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
                SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
                SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
            ),
            fileSystem.list(installStateDir).map { it.name }.toSet(),
        )
        assertTrue(prefs.all.isEmpty())
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
    fun `our keys are removed and host app keys are kept`() {
        val hostKeys = mapOf(
            "my.app.counter" to 3,
            "io.embraceapp.flag" to "a",
            "com.io.embrace.x" to "b",
            "embrace.user_session.backup" to "c",
            "emb.user_session_last_activity_ts" to 5000L,
            "networkcapturerule1" to 4,
        )
        prefs.seed(
            *ALL_LIVE_KEYS,
            "io.embrace.usersessionnumber" to 99,
            "io.embrace.bgactivitynumber" to 98,
            "io.embrace.lastappversion" to "0.9",
            "io.embrace.lastosversion" to "13",
            *hostKeys.toList().toTypedArray(),
        )
        assertTrue(migrate())
        assertEquals(hostKeys, prefs.all)
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
    fun `network capture and aei files are skipped when empty`() {
        prefs.seed(
            *IDENTITY,
            "io.embrace.networkcapturerule1" to 10,
            "io.embrace.networkcapturerulegone" to 1,
        )
        assertTrue(migrate())
        assertEquals(
            setOf(SdkStorageLayout.ORDINALS_FILE_NAME, SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME),
            fileSystem.list(installStateDir).map { it.name }.toSet(),
        )
    }

    @Test
    fun `network capture rules that aren't live or haven't been used are dropped`() {
        prefs.seed(
            *IDENTITY,
            "io.embrace.networkcapturerule1" to 5,
            "io.embrace.networkcapturerulerule-b" to 3,
            "io.embrace.networkcapturerulegone" to 1,
        )
        assertTrue(migrate())
        val rules = networkCaptureStore(liveRuleIds = setOf("1", "rule-b", "gone"))
        assertEquals(5, rules.remaining("1", 10))
        assertEquals(3, rules.remaining("rule-b", 3))
        assertEquals(10, rules.remaining("gone", 10))
    }

    @Test
    fun `failing to read live network capture rules drops their counts but migrates the rest`() {
        prefs.seed(*ALL_LIVE_KEYS)
        liveRulesProvider = { error("config not loaded") }
        assertTrue(migrate())
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFalse(fileSystem.exists(installStateDir / SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME))
        assertEquals("device-id", persistedState().deviceId)
        assertEquals(42, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `out of range network capture counts are clamped`() {
        liveRules = mapOf("lowered" to 10, "overused" to 3, "bad-max" to -1)
        prefs.seed(
            *IDENTITY,
            "io.embrace.networkcapturerulelowered" to 15,
            "io.embrace.networkcaptureruleoverused" to -2,
            "io.embrace.networkcapturerulebad-max" to 2,
        )
        assertTrue(migrate())
        val rules = networkCaptureStore()
        assertEquals(10, rules.remaining("lowered", 10))
        assertEquals(0, rules.remaining("overused", 3))
        assertTrue(fileSystem.exists(ordinalsPath()))
    }

    @Test
    fun `a prefs backup left by an interrupted write is migrated`() {
        prefs.seed(*IDENTITY, "io.embrace.sessionnumber" to 41)
        fileSystem.createDirectories(checkNotNull(prefsFile.parent))
        fileSystem.write(checkNotNull(prefsFile.parent) / "${prefsFile.name}.bak") { writeUtf8("<map />") }
        assertTrue(migrate(createPrefsFile = false))
        assertTrue(prefsLoaded)
        assertEquals("device-id", persistedState().deviceId)
        assertEquals(42, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
    }

    @Test
    fun `prefs that fail to load complete as a fresh install`() {
        prefs.seed(*ALL_LIVE_KEYS)
        val unloadable = object : SharedPreferences by prefs {
            override fun getAll(): Map<String, *> = error("load failed")
        }
        assertTrue(migrate(loaded = unloadable))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFreshInstall()
        assertEquals(ALL_LIVE_KEYS.size, prefs.all.size)
        assertFalse(migrate())
    }

    @Test
    fun `fresh install writes empty ordinals without loading prefs`() {
        assertTrue(migrate(createPrefsFile = false))
        assertFalse(prefsLoaded)
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
    }

    @Test
    fun `prefs without identity write empty ordinals and remove stray keys`() {
        prefs.seed("io.embrace.sessionnumber" to 41, "my.app.counter" to 3)
        assertTrue(migrate())
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
        assertEquals(mapOf("my.app.counter" to 3), prefs.all)
    }

    @Test
    fun `wrong-typed values migrate as absent`() {
        prefs.seed(
            *IDENTITY,
            "io.embrace.userid" to 1,
            "io.embrace.session.properties" to "{not json",
            "io.embrace.sessionnumber" to "41",
            "io.embrace.networkcapturerule1" to "5",
            "io.embrace.aeiHashCode" to "100_1",
        )
        assertTrue(migrate())
        val state = persistedState()
        assertEquals("device-id", state.deviceId)
        assertNull(state.userId)
        assertTrue(state.sessionProperties.isEmpty())
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
        assertEquals(10, networkCaptureStore().remaining("1", 10))
        assertTrue(deliveredAeiStore().ids().isEmpty())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `existing ordinals file skips the migration`() {
        prefs.seed(*ALL_LIVE_KEYS)
        MmapOrdinalStore.create(fileSystem, ordinalsPath(), emptyMap())
        assertFalse(migrate())
        assertFalse(prefsLoaded)
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(ALL_LIVE_KEYS.size, prefs.all.size)
    }

    @Test
    fun `failure writing persisted state completes as a fresh install`() {
        assertFailedWriteIsFreshInstall(SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME)
    }

    @Test
    fun `failure writing network capture drops only that file`() {
        assertFailedWriteDropsOnly(SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME)
        assertEquals(setOf("100_1", "200_2"), deliveredAeiStore().ids())
    }

    @Test
    fun `failure writing delivered aei ids drops only that file`() {
        assertFailedWriteDropsOnly(SdkStorageLayout.AEI_DELIVERED_FILE_NAME)
        assertEquals(5, networkCaptureStore().remaining("1", 10))
    }

    private fun assertFailedWriteDropsOnly(fileName: String) {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate(fs = failingMoveTo(installStateDir / fileName)))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFalse(fileSystem.exists(installStateDir / fileName))
        assertEquals("device-id", persistedState().deviceId)
        assertEquals("user-id", persistedState().userId)
        assertEquals(42, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
        assertTrue(prefs.all.isEmpty())
        assertFalse(migrate())
    }

    private fun assertFailedWriteIsFreshInstall(fileName: String) {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate(fs = failingMoveTo(installStateDir / fileName)))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFreshInstall()
        assertTrue(prefs.all.isEmpty())
        assertFalse(migrate())
    }

    private fun assertFreshInstall() {
        assertEquals(listOf(SdkStorageLayout.ORDINALS_FILE_NAME), fileSystem.list(installStateDir).map { it.name })
        assertEquals(1, openOrdinals().incrementAndGet(Ordinal.SESSION) { 1 })
        val state = FilePersistedSdkStateStore(
            fileSystem = fileSystem,
            path = installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
            worker = worker,
            errorHandler = errorHandler,
            clock = { -1L },
            uuidSource = { "generated" },
        ).snapshot()
        assertEquals("generated", state.deviceId)
    }

    @Test
    fun `failure writing ordinals completes as a fresh install`() {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate(fs = failingMoveTo(ordinalsPath(), failures = 1)))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertFreshInstall()
        assertTrue(prefs.all.isEmpty())
        assertFalse(migrate())
    }

    @Test
    fun `failure writing ordinals twice leaves no legacy data for a rerun`() {
        prefs.seed(*ALL_LIVE_KEYS)
        assertTrue(migrate(fs = failingMoveTo(ordinalsPath())))
        assertEquals(emptyList<Path>(), fileSystem.list(installStateDir))
        assertTrue(prefs.all.isEmpty())

        assertTrue(migrate())
        assertFreshInstall()
    }

    @Test
    fun `an abandoned migration writes nothing and still removes the keys`() {
        prefs.seed(*ALL_LIVE_KEYS)
        val gate = PrefsMigrationGate().apply { abandon {} }
        assertFalse(migrate(gate = gate))
        assertEquals(emptyList<Path>(), fileSystem.list(installStateDir))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `failing to remove keys still completes the migration`() {
        prefs.seed(*ALL_LIVE_KEYS)
        val uneditable = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor = error("edit failed")
        }
        assertTrue(migrate(loaded = uneditable))
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
        assertEquals(ALL_LIVE_KEYS.size, prefs.all.size)
        assertFalse(migrate())
    }

    private fun failingMoveTo(path: Path, failures: Int = Int.MAX_VALUE): FileSystem = object : ForwardingFileSystem(fileSystem) {
        private var remaining = failures

        override fun atomicMove(source: Path, target: Path) {
            if (target == path && remaining > 0) {
                remaining--
                throw IOException("disk full")
            }
            super.atomicMove(source, target)
        }
    }

    private fun migrate(
        appFramework: AppFramework = AppFramework.NATIVE,
        fs: FileSystem = fileSystem,
        createPrefsFile: Boolean = true,
        loaded: SharedPreferences = prefs,
        gate: PrefsMigrationGate = PrefsMigrationGate(),
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
                loaded
            },
            serializer = TestPlatformSerializer(),
            errorHandler = errorHandler,
            appFramework = appFramework,
            gate = gate,
            liveNetworkCaptureRules = liveRulesProvider,
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

    private fun networkCaptureStore(liveRuleIds: Set<String> = liveRules.keys) = FileNetworkCaptureRuleStore(
        fileSystem = fileSystem,
        path = installStateDir / SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
        worker = worker,
        errorHandler = errorHandler,
        liveRuleIds = { liveRuleIds },
    )

    private fun deliveredAeiStore() = FileDeliveredAeiStore(
        fileSystem = fileSystem,
        path = installStateDir / SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
        worker = worker,
        errorHandler = errorHandler,
    )
}
