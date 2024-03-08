package io.embrace.android.embracesdk.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Environment
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.fakes.system.mockStorageStatsManager
import io.embrace.android.embracesdk.fakes.system.mockWindowManager
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.BackgroundWorker
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
        private val packageInfo = PackageInfo()
        private val serializer = EmbraceSerializer()
        private lateinit var buildInfo: BuildInfo
        private lateinit var configService: ConfigService
        private lateinit var preferencesService: FakePreferenceService
        private lateinit var processStateService: ProcessStateService
        private lateinit var cpuInfoDelegate: FakeCpuInfoDelegate
        private val deviceArchitecture = FakeDeviceArchitecture()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(SdkLocalConfig::class)
            mockkStatic(MetadataUtils::class)
            mockkStatic(Environment::class)

            context = mockContext()
            buildInfo = BuildInfo("1234", "debug", "debug")
            configService = FakeConfigService()
            preferencesService = FakePreferenceService()
            processStateService = FakeProcessStateService()
            cpuInfoDelegate = FakeCpuInfoDelegate()

            initContext()
            initPreferences()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            unmockkAll()
        }

        @Suppress("DEPRECATION")
        private fun initContext() {
            packageInfo.versionName = "1.0.0"
            packageInfo.versionCode = 10

            every { context.getSystemService(Context.WINDOW_SERVICE) }.returns(mockk<WindowManager>())
            every { context.getSystemService(Context.STORAGE_STATS_SERVICE) }.returns(mockk<StorageStatsManager>())
            every { context.packageName }.returns("package-info")
            every { context.packageManager.getPackageInfo("package-info", 0) }.returns(packageInfo)
        }

        private fun initPreferences() {
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

    @Suppress("DEPRECATION")
    private fun getMetadataService() = EmbraceMetadataService.ofContext(
        context,
        buildInfo,
        configService,
        Embrace.AppFramework.UNITY,
        preferencesService,
        processStateService,
        BackgroundWorker(MoreExecutors.newDirectExecutorService()),
        mockStorageStatsManager(),
        mockWindowManager(),
        fakeClock,
        cpuInfoDelegate,
        deviceArchitecture,
        lazy { packageInfo.versionName },
        lazy { packageInfo.versionCode.toString() }
    )

    @Test
    fun `test unity framework`() {
        preferencesService.unityVersionNumber = "unityVersionNumber"
        preferencesService.unityBuildIdNumber = "unityBuildIdNumber"

        val metadataService = getMetadataService()
        val appInfo = serializer.toJson(metadataService.getAppInfo())

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

        val appInfo = serializer.toJson(metadataService.getAppInfo())

        assertTrue(appInfo.contains("\"unv\":\"unityVersionNumber\""))
        assertTrue(appInfo.contains("\"ubg\":\"unityBuildIdNumber\""))
    }
}
