package io.embrace.android.embracesdk.internal.ndk

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.util.Base64
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNdkServiceRepository
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService

internal class EmbraceNdkServiceTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
            mockkStatic(Uuid::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private lateinit var embraceNdkService: EmbraceNdkService
    private lateinit var context: Context
    private lateinit var handler: Handler
    private lateinit var storageManager: FakeStorageService
    private lateinit var metadataService: MetadataService
    private lateinit var configService: FakeConfigService
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var userService: FakeUserService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var sharedObjectLoader: SharedObjectLoader
    private lateinit var logger: EmbLogger
    private lateinit var delegate: NdkServiceDelegate.NdkDelegate
    private lateinit var repository: FakeNdkServiceRepository
    private lateinit var resources: Resources
    private lateinit var blockableExecutorService: BlockableExecutorService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private val deviceArchitecture = FakeDeviceArchitecture()
    private val serializer = EmbraceSerializer()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        val slot = slot<Runnable>()
        handler = mockk(relaxed = true) {
            every { postAtFrontOfQueue(capture(slot)) } answers {
                slot.captured.run()
                true
            }
        }
        storageManager = FakeStorageService()
        metadataService = FakeMetadataService()
        configService = FakeConfigService(autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(ndkEnabled = true))
        processStateService = FakeProcessStateService()
        deliveryService = FakeDeliveryService()
        userService = FakeUserService()
        preferencesService = FakePreferenceService()
        sessionPropertiesService = FakeSessionPropertiesService()
        sharedObjectLoader = mockk()
        logger = EmbLoggerImpl()
        delegate = mockk(relaxed = true)
        repository = FakeNdkServiceRepository()
        resources = mockk(relaxed = true)
        blockableExecutorService = BlockableExecutorService()
        sessionIdTracker = FakeSessionIdTracker()
        mockNativeSymbols()
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        every { sharedObjectLoader.loaded.get() } returns true
    }

    @After
    fun after() {
        clearAllMocks(staticMocks = false)

        val cacheDir = File("${storageManager.cacheDirectory}/ndk")
        if (cacheDir.exists()) {
            cacheDir.delete()
        }
        val filesDir = File("${storageManager.filesDirectory}/ndk")
        if (filesDir.exists()) {
            filesDir.delete()
        }
    }

    private fun initializeService() {
        embraceNdkService = spyk(
            EmbraceNdkService(
                context,
                storageManager,
                metadataService,
                processStateService,
                configService,
                userService,
                sessionPropertiesService,
                sharedObjectLoader,
                logger,
                repository,
                delegate,
                fakeBackgroundWorker(),
                deviceArchitecture,
                EmbraceSerializer(),
                handler
            ),
            recordPrivateCalls = true
        ).apply {
            initializeService(sessionIdTracker = sessionIdTracker)
        }
    }

    @Test
    fun `successful initialization attaches appropriate listeners`() {
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        assertEquals(1, userService.listeners.size)
        assertEquals(1, sessionIdTracker.listeners.size)
        assertEquals(1, sessionPropertiesService.listeners.size)
        assertEquals(1, processStateService.listeners.size)
    }

    @Test
    fun `failed native library loading attaches no listeners`() {
        every { sharedObjectLoader.loadEmbraceNative() } returns false
        initializeService()
        assertEquals(0, userService.listeners.size)
        assertEquals(0, sessionIdTracker.listeners.size)
        assertEquals(0, sessionPropertiesService.listeners.size)
        assertEquals(0, processStateService.listeners.size)
    }

    @Test
    fun `test updateSessionId where installSignals was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.updateSessionId("sessionId")
        verify(exactly = 1) { delegate._updateSessionId("sessionId") }
    }

    @Test
    fun `test onBackground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onBackground(0L)
        verify(exactly = 1) { delegate._updateAppState("background") }
    }

    @Test
    fun `test onSessionPropertiesUpdate where _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onSessionPropertiesUpdate(sessionPropertiesService.getProperties())
        val newDeviceMetaData =
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionPropertiesService.getProperties()
            )

        val expected = serializer.toJson(newDeviceMetaData)
        verify { delegate._updateMetaData(expected) }
    }

    @Test
    fun `test initialization with unity id and ndk enabled runs installSignals and updateDeviceMetaData`() {
        val unityId = "unityId"
        every { Uuid.getEmbUuid() } returns unityId

        configService.appFramework = AppFramework.UNITY
        initializeService()
        assertEquals(1, processStateService.listeners.size)

        val reportBasePath = storageManager.filesDirectory.absolutePath + "/ndk"
        val markerFilePath =
            storageManager.filesDirectory.absolutePath + "/" + CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME
        verify(exactly = 1) {
            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                "null",
                "foreground",
                unityId,
                Build.VERSION.SDK_INT,
                deviceArchitecture.is32BitDevice,
                false
            )
        }

        val newDeviceMetaData = serializer.toJson(
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionPropertiesService.getProperties()
            )
        )

        verify(exactly = 1) { delegate._updateMetaData(newDeviceMetaData) }
        assertEquals(embraceNdkService.unityCrashId, Uuid.getEmbUuid())
    }

    @Test
    fun `test metadata is updated after installation of the signal handler`() {
        every { Uuid.getEmbUuid() } returns "uuid"

        initializeService()
        assertEquals(1, processStateService.listeners.size)

        val reportBasePath = storageManager.filesDirectory.absolutePath + "/ndk"
        val markerFilePath =
            storageManager.filesDirectory.absolutePath + "/" + CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME

        verifyOrder {
            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                "null",
                "foreground",
                "uuid",
                Build.VERSION.SDK_INT,
                deviceArchitecture.is32BitDevice,
                false
            )
            delegate._updateMetaData(any())
        }
    }

    @Test
    fun `test getUnityCrashId`() {
        configService.appFramework = AppFramework.UNITY
        every { Uuid.getEmbUuid() } returns "unityId"
        initializeService()
        val uuid = embraceNdkService.unityCrashId
        assertEquals(uuid, "unityId")
    }

    @Test
    fun `test onUserInfoUpdate where _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onUserInfoUpdate()
        val newDeviceMetaData =
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionPropertiesService.getProperties()
            )

        val expected = serializer.toJson(newDeviceMetaData)
        verify { delegate._updateMetaData(expected) }
    }

    @Test
    fun `test onForeground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onForeground(true, 10)
        verify(exactly = 1) { delegate._updateAppState("foreground") }
    }

    @Test
    fun `test getLatestNativeCrash does nothing if there are no matchingFiles`() {
        initializeService()
        val result = embraceNdkService.getLatestNativeCrash()
        assertNull(result)
        verify(exactly = 0) { delegate._getCrashReport(any()) }
    }

    @Test
    fun `test getSymbolsForCurrentArch`() {
        mockNativeSymbols()
        initializeService()

        val result = embraceNdkService.symbolsForCurrentArch
        assert(result != null)
        assert(result?.containsKey("symbol1") ?: false)
        assert(result?.getOrDefault("symbol1", "") == "test")
    }

    @Test
    fun `test getLatestNativeCrash catches an exception if _getCrashReport returns an empty string`() {
        repository.addCrashFiles(File.createTempFile("test", "test"))
        initializeService()
        val crashData = embraceNdkService.getLatestNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test getLatestNativeCrash catches an exception if _getCrashReport returns invalid json syntax`() {
        repository.addCrashFiles(File.createTempFile("test", "test"))
        every { Uuid.getEmbUuid() } returns "unityId"

        val json = "{\n" +
            "  \"sid\": [\n" +
            "    {\n" +
            "    }\n" +
            "  ]\n" +
            "}"
        every { delegate._getCrashReport(any()) } returns json

        initializeService()
        val crashData = embraceNdkService.getLatestNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test getLatestNativeCrash when a native crash was captured`() {
        repository.addCrashFiles(
            nativeCrashFile = File.createTempFile("test", "crash-test")
        )
        every { delegate._getCrashReport(any()) } returns getNativeCrashRaw()

        configService.appFramework = AppFramework.UNITY

        initializeService()
        mockNativeSymbols()

        val result = checkNotNull(embraceNdkService.getLatestNativeCrash())
        with(result) {
            assertNotNull(crash)
            assertNull(map)
            assertNull(errors)
        }
    }

    @Test
    fun `test getLatestNativeCrash when there is no native crash does not execute crash files logic`() {
        configService.appFramework = AppFramework.UNITY
        initializeService()

        val result = embraceNdkService.getLatestNativeCrash()
        assertNull(result)
    }

    @Test
    fun `test initialization does not does not install signals and create directories if loadEmbraceNative is false`() {
        every { sharedObjectLoader.loadEmbraceNative() } returns false
        initializeService()
        verify(exactly = 0) { embraceNdkService["installSignals"]() }
        verify(exactly = 0) { embraceNdkService["createCrashReportDirectory"]() }
    }

    @Test
    fun `initialization happens`() {
        blockableExecutorService.blockingMode = true
        initializeService()
        assertNativeSignalHandlerInstalled()
    }

    @Test
    fun `getNativeCrashes returns all the crashes in the repository and doesn't invoke delete`() {
        every { delegate._getCrashReport(any()) } returns getNativeCrashRaw()
        initializeService()
        repository.addCrashFiles(File.createTempFile("file1", "tmp"))
        repository.addCrashFiles(File.createTempFile("file2", "temp"))
        assertEquals(2, embraceNdkService.getNativeCrashes().size)
        assertEquals(2, embraceNdkService.getNativeCrashes().size)
    }

    @Test
    fun `getLatestNativeCrash returns only one crash even if there are many and deletes them all`() {
        every { delegate._getCrashReport(any()) } returns getNativeCrashRaw()
        initializeService()
        repository.addCrashFiles(File.createTempFile("file1", "tmp"))
        repository.addCrashFiles(File.createTempFile("file2", "temp"))
        assertNotNull(embraceNdkService.getLatestNativeCrash())
        assertEquals(0, embraceNdkService.getNativeCrashes().size)
    }

    private fun assertNativeSignalHandlerInstalled() {
        verify(exactly = 1) {
            delegate._installSignalHandlers(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    private fun getNativeCrashRaw() = ResourceReader.readResourceAsText("native_crash_raw.txt")

    private fun mockNativeSymbols() {
        mockkStatic(Base64::class)
        val resourceId = 10
        val nativeSymbolsJson = "{\"symbols\":{\"arm64-v8a\":{\"symbol1\":\"test\"}}}"

        every { context.resources } returns resources
        every { context.packageName } returns "package-name"
        every { resources.getString(resourceId) } returns "result"
        every {
            resources.getIdentifier(
                "emb_ndk_symbols",
                "string",
                "package-name"
            )
        } returns resourceId
        every { resources.getString(resourceId) } returns nativeSymbolsJson
        every {
            Base64.decode(
                nativeSymbolsJson,
                Base64.DEFAULT
            )
        } returns nativeSymbolsJson.encodeToByteArray()
    }
}
