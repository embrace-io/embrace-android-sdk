package io.embrace.android.embracesdk.internal.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Environment
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

internal class EmbraceMetadataServiceTest {

    companion object {
        private val context: Context = mockk(relaxed = true)
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
            mockkStatic(Environment::class)

            initContext()
            initPreferences()

            every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))

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
            every { preferencesService.jailbroken }.returns(true)
            every { preferencesService.screenResolution }.returns("200x300")
        }
    }

    private val configService: FakeConfigService =
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
    private fun getMetadataService(framework: AppFramework = AppFramework.NATIVE): EmbraceMetadataService {
        configService.appFramework = framework
        return EmbraceMetadataService(
            context,
            windowManager,
            storageStatsManager,
            SystemInfo(),
            buildInfo,
            configService,
            lazy { packageInfo.versionName },
            lazy { packageInfo.versionCode.toString() },
            preferencesService,
            hostedSdkVersionInfo,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            fakeClock,
            cpuInfoDelegate,
            fakeArchitecture,
            FakeEmbLogger(),
            "1",
            "33"
        ).apply { precomputeValues() }
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

        val deviceInfo = serializer.toJson(getMetadataService().getDeviceInfo())

        Assert.assertTrue(deviceInfo.contains("\"jb\":true"))
        Assert.assertTrue(deviceInfo.contains("\"sr\":\"200x300\""))
        Assert.assertTrue(deviceInfo.contains("\"da\":\"arm64-v8a\""))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `test device info without running async operations`() {
        every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))

        val metadataService = EmbraceMetadataService(
            context,
            windowManager,
            storageStatsManager,
            SystemInfo(),
            buildInfo,
            configService,
            lazy { packageInfo.versionName },
            lazy { packageInfo.versionCode.toString() },
            preferencesService,
            hostedSdkVersionInfo,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            fakeClock,
            cpuInfoDelegate,
            fakeArchitecture,
            FakeEmbLogger(),
            "1",
            "33"
        )

        val deviceInfo = serializer.toJson(metadataService.getDeviceInfo())

        Assert.assertTrue(deviceInfo.contains("\"da\":\"arm64-v8a\""))
    }

    @Test
    fun `test async additional device info`() {
        every { preferencesService.cpuName } returns null
        every { preferencesService.egl } returns null

        val metadataService = getMetadataService()
        val info = metadataService.getDeviceInfo()
        assertEquals("fake_cpu", info.cpuName)
        assertEquals("fake_egl", info.egl)
    }
}
