package io.embrace.android.embracesdk.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Environment
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.fakes.system.mockStorageStatsManager
import io.embrace.android.embracesdk.fakes.system.mockWindowManager
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

internal class EmbraceMetadataServiceTest {

    companion object {
        private val context: Context = mockContext()
        private val buildInfo: BuildInfo = BuildInfo("1234", "debug", "free")
        private val packageInfo = PackageInfo()
        private val serializer = EmbraceSerializer()
        private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
        private val preferencesService: EmbracePreferencesService = mockk(relaxed = true)
        private val fakeClock = FakeClock()
        private val cpuInfoDelegate: FakeCpuInfoDelegate = FakeCpuInfoDelegate()
        private val fakeArchitecture = FakeDeviceArchitecture()
        private val storageStatsManager = mockk<StorageStatsManager>()
        private val windowManager = mockk<WindowManager>()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(MetadataUtils::class)
            mockkStatic(Environment::class)

            initContext()
            initPreferences()

            every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
            every { MetadataUtils.getInternalStorageFreeCapacity(any()) }.returns(123L)

            hostedSdkVersionInfo = HostedSdkVersionInfo(
                preferencesService
            )
        }

        @After
        fun tearDown() {
            unmockkAll()
        }

        @Suppress("DEPRECATION")
        private fun initContext() {
            packageInfo.versionName = "1.0.0"
            @Suppress("DEPRECATION")
            packageInfo.versionCode = 10

            every { context.getSystemService(Context.WINDOW_SERVICE) }.returns(mockk<WindowManager>())
            every { context.getSystemService(Context.STORAGE_STATS_SERVICE) }.returns(mockk<StorageStatsManager>())
            every { context.packageName }.returns("package-info")
            every { context.packageManager.getPackageInfo("package-info", 0) }.returns(packageInfo)
        }

        private fun initPreferences() {
            every { preferencesService.appVersion }.returns("app-version")
            every { preferencesService.osVersion }.returns("os-version")

            // to test Device Info:
            every { MetadataUtils.isJailbroken() }.returns(true)
            every { preferencesService.jailbroken }.returns(true)
            every { preferencesService.screenResolution }.returns("200x300")
        }
    }

    private val activityService = FakeProcessStateService()
    private val configService: ConfigService =
        FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = {
                    LocalConfig("appId", true, SdkLocalConfig())
                }
            ),
            sdkModeBehavior = fakeSdkModeBehavior(
                localCfg = {
                    LocalConfig("appId", false, SdkLocalConfig())
                }
            )
        )

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )
    }

    @Suppress("DEPRECATION")
    private fun getMetadataService(framework: Embrace.AppFramework = Embrace.AppFramework.NATIVE): EmbraceMetadataService {
        return EmbraceMetadataService.ofContext(
            context,
            AppEnvironment.Environment.UNKNOWN,
            buildInfo,
            configService,
            framework,
            preferencesService,
            activityService,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            storageStatsManager,
            windowManager,
            fakeClock,
            cpuInfoDelegate,
            fakeArchitecture,
            lazy { packageInfo.versionName },
            lazy { packageInfo.versionCode.toString() },
            hostedSdkVersionInfo
        ).apply { precomputeValues() }
    }

    @Test
    @Throws(InterruptedException::class)
    fun `test EmbraceMetadataService creation loads AppVersion lazily`() {
        verify(exactly = 0) { preferencesService.appVersion }
        getMetadataService().isAppUpdated()
        verify(exactly = 1) { preferencesService.appVersion }
    }

    @Test
    @Throws(InterruptedException::class)
    fun `test EmbraceMetadataService creation loads OsVersion lazily`() {
        verify(exactly = 0) { preferencesService.osVersion }
        getMetadataService().isOsUpdated()
        verify(exactly = 1) { preferencesService.osVersion }
    }

    @Test
    @Throws(InterruptedException::class)
    fun `test EmbraceMetadataService creation loads DeviceIdentifier lazily`() {
        every { preferencesService.deviceIdentifier }.returns("device-id")

        verify(exactly = 0) { preferencesService.deviceIdentifier }
        getMetadataService().getDeviceId()
        verify(exactly = 1) { preferencesService.deviceIdentifier }
    }

    @Test
    fun `test app info`() { // FIXME: causing mockk error
        every { preferencesService.appVersion }.returns(null)
        every { preferencesService.osVersion }.returns(null)
        every { preferencesService.unityVersionNumber }.returns(null)
        every { preferencesService.unityBuildIdNumber }.returns(null)
        every { preferencesService.reactNativeVersionNumber }.returns(null)
        every { preferencesService.javaScriptBundleId }.returns(null)
        every { preferencesService.javaScriptBundleURL }.returns(null)

        val obj = getMetadataService().getAppInfo()
        val expectedInfo = ResourceReader.readResourceAsText("metadata_appinfo_expected.json")
            .replace("{versionName}", checkNotNull(obj.sdkVersion))
            .replace("{versionCode}", checkNotNull(obj.sdkSimpleVersion))
            .filter { !it.isWhitespace() }

        val appInfo = serializer.toJson(obj)
        assertEquals(expectedInfo, appInfo.replace(" ", ""))
    }

    @Test
    fun `test startup complete`() {
        every { preferencesService.installDate }.returns(null)
        getMetadataService().applicationStartupComplete()

        verify(exactly = 1) { preferencesService.appVersion = any() }
        verify(exactly = 1) { preferencesService.osVersion = any() }
        verify(exactly = 1) { preferencesService.deviceIdentifier = any() }
        verify(exactly = 1) { preferencesService.installDate = any() }
    }

    @Test
    fun `test startup complete if it is not the first time`() {
        every { preferencesService.installDate }.returns(1234L)
        getMetadataService().applicationStartupComplete()
        verify(exactly = 0) { preferencesService.installDate = any() }
    }

    @Test
    fun `test device info`() {
        every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
        every { MetadataUtils.getInternalStorageTotalCapacity(any()) }.returns(123L)
        every { MetadataUtils.getLocale() }.returns("en-US")

        val deviceInfo = serializer.toJson(getMetadataService().getDeviceInfo())

        verify(exactly = 1) { MetadataUtils.getDeviceManufacturer() }
        verify(exactly = 1) { MetadataUtils.getModel() }
        verify(exactly = 1) { MetadataUtils.getLocale() }
        verify(exactly = 1) { MetadataUtils.getInternalStorageTotalCapacity(any()) }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemType() }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemVersion() }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemVersionCode() }
        verify(exactly = 1) { MetadataUtils.getTimezoneId() }
        verify(exactly = 1) { MetadataUtils.getNumberOfCores() }

        assertTrue(deviceInfo.contains("\"jb\":true"))
        assertTrue(deviceInfo.contains("\"sr\":\"200x300\""))
        assertTrue(deviceInfo.contains("\"da\":\"arm64-v8a\""))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `test device info without running async operations`() {
        every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
        every { MetadataUtils.getInternalStorageTotalCapacity(any()) }.returns(123L)
        every { MetadataUtils.getLocale() }.returns("en-US")

        val metadataService = EmbraceMetadataService.ofContext(
            context,
            AppEnvironment.Environment.PROD,
            buildInfo,
            configService,
            Embrace.AppFramework.NATIVE,
            preferencesService,
            activityService,
            BackgroundWorker(BlockingScheduledExecutorService()),
            mockStorageStatsManager(),
            mockWindowManager(),
            fakeClock,
            cpuInfoDelegate,
            fakeArchitecture,
            lazy { packageInfo.versionName },
            lazy { packageInfo.versionCode.toString() },
            hostedSdkVersionInfo
        )

        val deviceInfo = serializer.toJson(metadataService.getDeviceInfo())

        verify(exactly = 1) { MetadataUtils.getDeviceManufacturer() }
        verify(exactly = 1) { MetadataUtils.getModel() }
        verify(exactly = 1) { MetadataUtils.getLocale() }
        verify(exactly = 1) { MetadataUtils.getInternalStorageTotalCapacity(any()) }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemType() }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemVersion() }
        verify(exactly = 1) { MetadataUtils.getOperatingSystemVersionCode() }
        verify(exactly = 1) { MetadataUtils.getTimezoneId() }
        assertTrue(deviceInfo.contains("\"da\":\"arm64-v8a\""))
    }

    @Test
    fun `test public methods`() {
        val metadataService = getMetadataService()

        activityService.isInBackground = true
        assertEquals("background", metadataService.getAppState())

        activityService.isInBackground = false
        assertEquals("foreground", metadataService.getAppState())

        assertEquals("appId", metadataService.getAppId())
        assertEquals("10", metadataService.getAppVersionCode())
    }

    @Test
    fun `test disk usage`() {
        every {
            MetadataUtils.getDeviceDiskAppUsage(any(), any(), any())
        }.returns(123L)

        val service = getMetadataService()
        service.asyncRetrieveDiskUsage(true)

        assertEquals(123L, service.getDiskUsage()?.appDiskUsage)
    }

    @Test
    fun `test async additional device info`() {
        every { preferencesService.cpuName } returns null
        every { preferencesService.egl } returns null

        val metadataService = getMetadataService()

        assertEquals("fake_cpu", metadataService.getCpuName())
        assertEquals("fake_egl", metadataService.getEgl())
    }
}
