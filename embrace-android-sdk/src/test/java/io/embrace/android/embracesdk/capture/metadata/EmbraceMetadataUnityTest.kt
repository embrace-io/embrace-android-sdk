package io.embrace.android.embracesdk.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Environment
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.app.AppFramework
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.session.ActivityService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceMetadataUnityTest {

    companion object {
        private val fakeClock = FakeClock()
        private lateinit var context: Context
        private lateinit var buildInfo: BuildInfo
        private lateinit var configService: ConfigService
        private lateinit var preferencesService: FakePreferenceService
        private lateinit var activityService: ActivityService
        private lateinit var cacheService: EmbraceCacheService
        private lateinit var cpuInfoDelegate: EmbraceCpuInfoDelegate
        private val deviceArchitecture = FakeDeviceArchitecture()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(SdkLocalConfig::class)
            mockkStatic(MetadataUtils::class)
            mockkStatic(Environment::class)

            context = mockk(relaxed = true)
            buildInfo = mockk()
            configService = mockk(relaxed = true)
            preferencesService = FakePreferenceService()
            activityService = FakeActivityService()
            cacheService = mockk()
            cpuInfoDelegate = mockk(relaxed = true)

            initContext()
            initPreferences()
        }

        @AfterClass
        fun tearDown() {
            unmockkAll()
        }

        private fun initContext() {
            val packageInfo = PackageInfo()
            packageInfo.versionName = "1.0.0"
            @Suppress("DEPRECATION")
            packageInfo.versionCode = 10

            every { context.getSystemService(Context.WINDOW_SERVICE) }.returns(mockk<WindowManager>())
            every { context.getSystemService(Context.STORAGE_STATS_SERVICE) }.returns(mockk<StorageStatsManager>())
            every { context.packageName }.returns("package-info")
            every { context.packageManager.getPackageInfo("package-info", 0) }.returns(packageInfo)
        }

        private fun initPreferences() {
            every { buildInfo.buildId }.returns("1234")
            every { buildInfo.buildType }.returns("debug")
            every { buildInfo.buildFlavor }.returns("debug")

            preferencesService.appVersion = "app-version"
            preferencesService.osVersion = "os-version"
        }
    }

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )
    }

    private fun getMetadataService() = EmbraceMetadataService.ofContext(
        context,
        buildInfo,
        configService,
        AppFramework.UNITY,
        preferencesService,
        activityService,
        MoreExecutors.newDirectExecutorService(),
        mockk(),
        mockk(),
        mockk(),
        fakeClock,
        cpuInfoDelegate,
        deviceArchitecture
    )

    @Test
    fun `test unity framework`() {
        preferencesService.unityVersionNumber = "unityVersionNumber"
        preferencesService.unityBuildIdNumber = "unityBuildIdNumber"

        val metadataService = getMetadataService()
        val appInfo = metadataService.getAppInfo().toJson()

        assertTrue(appInfo.contains("\"unv\":\"unityVersionNumber\""))
        assertTrue(appInfo.contains("\"ubg\":\"unityBuildIdNumber\""))
    }

    @Test
    fun `test preferences null at beginning`() {
        preferencesService.unityVersionNumber = null
        preferencesService.unityBuildIdNumber = null

        val metadataService = getMetadataService()

        preferencesService.unityVersionNumber = "unityVersionNumber"
        preferencesService.unityBuildIdNumber = "unityBuildIdNumber"

        val appInfo = metadataService.getAppInfo().toJson()

        assertTrue(appInfo.contains("\"unv\":\"unityVersionNumber\""))
        assertTrue(appInfo.contains("\"ubg\":\"unityBuildIdNumber\""))
    }
}
