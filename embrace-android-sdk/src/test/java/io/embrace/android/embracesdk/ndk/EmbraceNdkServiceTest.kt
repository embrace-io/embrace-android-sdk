package io.embrace.android.embracesdk.ndk

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Base64
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.fakes.system.mockResources
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService

internal class EmbraceNdkServiceTest {

    companion object {
        private lateinit var embraceNdkService: EmbraceNdkService
        private lateinit var context: Context
        private lateinit var storageManager: FakeStorageService
        private lateinit var metadataService: MetadataService
        private lateinit var configService: ConfigService
        private lateinit var activityService: FakeProcessStateService
        private lateinit var localConfig: LocalConfig
        private lateinit var deliveryService: FakeDeliveryService
        private lateinit var userService: UserService
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var appFramework: Embrace.AppFramework
        private lateinit var sharedObjectLoader: SharedObjectLoader
        private lateinit var logger: InternalEmbraceLogger
        private lateinit var delegate: NdkServiceDelegate.NdkDelegate
        private lateinit var repository: EmbraceNdkServiceRepository
        private lateinit var resources: Resources
        private val deviceArchitecture = FakeDeviceArchitecture()
        private val serializer = EmbraceSerializer()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
            mockkStatic(Uuid::class)
            mockkStatic(Embrace::class)
            context = mockContext()
            storageManager = FakeStorageService()
            metadataService = FakeAndroidMetadataService()
            localConfig = LocalConfig("", false, SdkLocalConfig())
            configService =
                FakeConfigService(
                    autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(localCfg = {
                        localConfig
                    })
                )
            activityService = FakeProcessStateService()
            deliveryService = FakeDeliveryService()
            userService = FakeUserService()
            sessionProperties = EmbraceSessionProperties(FakePreferenceService(), configService)
            appFramework = Embrace.AppFramework.NATIVE
            sharedObjectLoader = mockk()
            logger = InternalEmbraceLogger()
            delegate = mockk(relaxed = true)
            repository = mockk(relaxUnitFun = true)
            resources = mockResources()
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @After
    fun after() {
        clearAllMocks(
            answers = false,
            staticMocks = false,
            objectMocks = false
        )

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
        embraceNdkService = EmbraceNdkService(
            context,
            storageManager,
            metadataService,
            activityService,
            configService,
            deliveryService,
            userService,
            sessionProperties,
            appFramework,
            sharedObjectLoader,
            logger,
            repository,
            delegate,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            deviceArchitecture,
            EmbraceSerializer()
        )
    }

    @Test
    fun `test updateSessionId where installSignals was not executed and isInstalled false`() {
        enableNdk(false)
        initializeService()
        embraceNdkService.updateSessionId("sessionId")
        verify(exactly = 0) { delegate._updateSessionId("sessionId") }
    }

    @Test
    fun `test updateSessionId where installSignals was executed and isInstalled true`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        embraceNdkService.updateSessionId("sessionId")
        verify(exactly = 1) { delegate._updateSessionId("sessionId") }
    }

    @Test
    fun `test onBackground doesn't run _updateAppState when _updateMetaData was not executed and isInstalled false`() {
        enableNdk(false)
        initializeService()
        embraceNdkService.onBackground(0L)
        verify(exactly = 0) { delegate._updateAppState("background") }
    }

    @Test
    fun `test onBackground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        embraceNdkService.onBackground(0L)
        verify(exactly = 1) { delegate._updateAppState("background") }
    }

    @Test
    fun `test onSessionPropertiesUpdate where _updateMetaData was not executed and isInstalled false`() {
        enableNdk(false)
        initializeService()
        embraceNdkService.onSessionPropertiesUpdate(sessionProperties.get())
        verify(exactly = 0) { delegate._updateMetaData(any()) }
    }

    @Test
    fun `test onSessionPropertiesUpdate where _updateMetaData was executed and isInstalled true`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        embraceNdkService.onSessionPropertiesUpdate(sessionProperties.get())
        val newDeviceMetaData =
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )

        val expected = serializer.toJson(newDeviceMetaData)
        verify { delegate._updateMetaData(expected) }
    }

    @Test
    fun `test initialization with unity id and ndk enabled runs installSignals and updateDeviceMetaData`() {
        every { Uuid.getEmbUuid() } returns "unityId"
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        appFramework = Embrace.AppFramework.UNITY
        initializeService()
        assertTrue(activityService.listeners.contains(embraceNdkService))

        val reportBasePath = storageManager.filesDirectory.value.absolutePath + "/ndk"
        val markerFilePath =
            storageManager.filesDirectory.value.absolutePath + "/" + CrashFileMarker.CRASH_MARKER_FILE_NAME
        verify(exactly = 1) {
            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                any(),
                "null",
                metadataService.getAppState(),
                embraceNdkService.getUnityCrashId(),
                Build.VERSION.SDK_INT,
                deviceArchitecture.is32BitDevice,
                ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED
            )
        }

        val newDeviceMetaData = serializer.toJson(
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )
        )

        verify(exactly = 1) { delegate._updateMetaData(newDeviceMetaData) }
        assertEquals(embraceNdkService.getUnityCrashId(), Uuid.getEmbUuid())
    }

    @Test
    fun `test metadata is updated after installation of the signal handler`() {
        every { Uuid.getEmbUuid() } returns "uuid"
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true

        val metaData = serializer.toJson(
            NativeCrashMetadata(
                metadataService.getLightweightAppInfo(),
                metadataService.getLightweightDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )
        )

        initializeService()
        assertTrue(activityService.listeners.contains(embraceNdkService))

        val reportBasePath = storageManager.filesDirectory.value.absolutePath + "/ndk"
        val markerFilePath =
            storageManager.filesDirectory.value.absolutePath + "/" + CrashFileMarker.CRASH_MARKER_FILE_NAME

        verifyOrder {
            metadataService.getLightweightAppInfo()
            metadataService.getLightweightDeviceInfo()

            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                metaData,
                "null",
                metadataService.getAppState(),
                "uuid",
                Build.VERSION.SDK_INT,
                deviceArchitecture.is32BitDevice,
                ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED
            )

            metadataService.getAppInfo()
            metadataService.getDeviceInfo()

            delegate._updateMetaData(metaData)
        }
    }

    @Test
    fun `test getUnityCrashId`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        appFramework = Embrace.AppFramework.UNITY
        every { Uuid.getEmbUuid() } returns "unityId"
        initializeService()
        val uuid = embraceNdkService.getUnityCrashId()
        assertEquals(uuid, "unityId")
    }

    @Test
    fun `test initialization with ndk disabled doesn't run _installSignalHandlers and _updateMetaData`() {
        enableNdk(false)
        initializeService()
        val reportBasePath = storageManager.filesDirectory.value.absolutePath + "/ndk"
        val markerFilePath = storageManager.filesDirectory.value.absolutePath + "/crash_file_marker"

        verify(exactly = 0) {
            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                "{}",
                "null",
                metadataService.getAppState(),
                embraceNdkService.getUnityCrashId(),
                Build.VERSION.SDK_INT,
                deviceArchitecture.is32BitDevice,
                ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED
            )
        }

        val newDeviceMetaData =
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )

        verify(exactly = 0) { delegate._updateMetaData(serializer.toJson(newDeviceMetaData)) }
    }

    @Test
    fun `test testCrash where isCpp is true`() {
        initializeService()
        embraceNdkService.testCrash(true)
        verify { delegate._testNativeCrash_CPP() }
        verify(exactly = 0) { delegate._testNativeCrash_C() }
    }

    @Test
    fun `test testCrash where isCpp is false`() {
        initializeService()
        embraceNdkService.testCrash(false)
        verify { delegate._testNativeCrash_C() }
        verify(exactly = 0) { delegate._testNativeCrash_CPP() }
    }

    @Test
    fun `test onUserInfoUpdate where _updateMetaData was not executed and isInstalled false`() {
        enableNdk(false)
        initializeService()
        embraceNdkService.onUserInfoUpdate()
        verify(exactly = 0) { delegate._updateMetaData(any()) }
    }

    @Test
    fun `test onUserInfoUpdate where _updateMetaData was executed and isInstalled true`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        embraceNdkService.onUserInfoUpdate()
        val newDeviceMetaData =
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )

        val expected = serializer.toJson(newDeviceMetaData)
        verify { delegate._updateMetaData(expected) }
    }

    @Test
    fun `test onForeground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        initializeService()
        embraceNdkService.onForeground(true, 1, 10)
        verify(exactly = 1) { delegate._updateAppState("active") }
    }

    @Test
    fun `test onForeground doesn't run _updateAppState when _updateMetaData was not executed and isInstalled false`() {
        enableNdk(false)
        initializeService()
        embraceNdkService.onForeground(true, 1, 100)
        verify(exactly = 0) { delegate._updateAppState("active") }
    }

    @Test
    fun `test checkForNativeCrash does nothing if there are no matchingFiles`() {
        every { repository.sortNativeCrashes(false) } returns listOf()
        initializeService()
        val result = embraceNdkService.checkForNativeCrash()
        assertNull(result)
        verify { repository.sortNativeCrashes(false) }
        verify(exactly = 0) { delegate._getCrashReport(any()) }
        verify(exactly = 0) { repository.errorFileForCrash(any()) }
        verify(exactly = 0) { repository.mapFileForCrash(any()) }
        verify(exactly = 0) {
            repository.deleteFiles(
                any(),
                any(),
                any(),
                any() as NativeCrashData
            )
        }
        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `test getSymbolsForCurrentArch`() {
        mockkStatic(Base64::class)
        val resourceId = 10
        val nativeSymbolsJson = "{\"symbols\":{\"arm64-v8a\":{\"symbol1\":\"test\"}}}"

        enableNdk(true)
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
        initializeService()

        val result = embraceNdkService.getSymbolsForCurrentArch()
        assert(result != null)
        assert(result?.containsKey("symbol1") ?: false)
        assert(result?.getOrDefault("symbol1", "") == "test")
    }

    @Test
    fun `test checkForNativeCrash catches an exception if _getCrashReport returns an empty string`() {
        val crashFile = File.createTempFile("test", "test")
        every { repository.sortNativeCrashes(false) } returns listOf(crashFile)
        every { delegate._getCrashReport(any()) } returns ""
        initializeService()
        val crashData = embraceNdkService.checkForNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test checkForNativeCrash catches an exception if _getCrashReport returns invalid json syntax`() {
        val crashFile = File.createTempFile("test", "test")
        every { Uuid.getEmbUuid() } returns "unityId"
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true

        val json = "{\n" +
            "  \"sid\": [\n" +
            "    {\n" +
            "    }\n" +
            "  ]\n" +
            "}"
        every { repository.sortNativeCrashes(false) } returns listOf(crashFile)
        every { delegate._getCrashReport(any()) } returns json

        initializeService()
        val crashData = embraceNdkService.checkForNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test checkForNativeCrash when a native crash was captured`() {
        val crashFile: File = File.createTempFile("test", "test")
        val errorFile: File = File.createTempFile("test", "test")
        val mapFile: File = File.createTempFile("test", "test")

        every { Uuid.getEmbUuid() } returns "unityId"
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns true
        every { repository.errorFileForCrash(crashFile) } returns errorFile
        every { repository.mapFileForCrash(crashFile) } returns mapFile

        every { delegate._getCrashReport(any()) } returns getNativeCrashRaw()
        every { repository.sortNativeCrashes(false) } returns listOf(crashFile)

        appFramework = Embrace.AppFramework.UNITY
        initializeService()
        val mockedNdkService = spyk(embraceNdkService, recordPrivateCalls = true)
        every { mockedNdkService.getSymbolsForCurrentArch() } returns mockk()

        val result = mockedNdkService.checkForNativeCrash()
        assertNotNull(result)

        verify { mockedNdkService["getNativeCrashErrors"](any() as NativeCrashData, errorFile) }
        verify(exactly = 1) { repository.sortNativeCrashes(false) }
        verify(exactly = 1) { delegate._getCrashReport(any()) }
        verify(exactly = 1) { repository.errorFileForCrash(crashFile) }
        verify(exactly = 1) { repository.mapFileForCrash(crashFile) }
    }

    @Test
    fun `test checkForNativeCrash when there is no native crash does not execute crash files logic`() {
        every { repository.sortNativeCrashes(false) } returns listOf()

        appFramework = Embrace.AppFramework.UNITY
        initializeService()

        val result = embraceNdkService.checkForNativeCrash()
        assertNull(result)

        verify(exactly = 1) { repository.sortNativeCrashes(false) }
        verify(exactly = 0) { delegate._getCrashReport(any()) }
        verify(exactly = 0) { repository.errorFileForCrash(any()) }
        verify(exactly = 0) { repository.mapFileForCrash(any()) }
        assertTrue(deliveryService.sentMoments.isEmpty())
        verify(exactly = 0) {
            repository.deleteFiles(
                any() as File,
                any() as File,
                any() as File,
                any() as NativeCrashData
            )
        }
        assertTrue(deliveryService.sentMoments.isEmpty())
    }

    @Test
    fun `test initialization does not does not install signals and create directories if loadEmbraceNative is false`() {
        enableNdk(true)
        every { sharedObjectLoader.loadEmbraceNative() } returns false
        initializeService()
        val mockedNdkService = spyk(embraceNdkService, recordPrivateCalls = false)
        verify(exactly = 0) { mockedNdkService["installSignals"]() }
        verify(exactly = 0) { mockedNdkService["createCrashReportDirectory"]() }
    }

    private fun getNativeCrashRaw() = ResourceReader.readResourceAsText("native_crash_raw.txt")

    private fun enableNdk(enabled: Boolean) {
        localConfig = LocalConfig("", enabled, SdkLocalConfig())
    }
}
