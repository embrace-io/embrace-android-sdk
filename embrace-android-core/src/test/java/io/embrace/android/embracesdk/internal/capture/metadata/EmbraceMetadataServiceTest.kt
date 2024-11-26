package io.embrace.android.embracesdk.internal.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Environment
import android.view.WindowManager
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.buildinfo.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.DeviceImpl
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.internal.injection.PackageVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

internal class EmbraceMetadataServiceTest {

    companion object {
        private val context: Context = mockk(relaxed = true)
        private val buildInfo: BuildInfo = BuildInfo("1234", "debug", "free", "bundle-id")
        private val packageInfo = PackageInfo()
        private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
        private lateinit var ref: EmbraceMetadataService
        private val preferencesService: EmbracePreferencesService = mockk(relaxed = true)
        private val fakeClock = FakeClock()
        private val fakeArchitecture = FakeDeviceArchitecture()
        private val storageStatsManager = mockk<StorageStatsManager>()

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
            packageInfo.packageName = "com.embrace.fake"

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

    private val configService: FakeConfigService = FakeConfigService()

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
    private fun getMetadataService(
        framework: AppFramework = AppFramework.NATIVE,
        precompute: Boolean = true,
    ): EmbraceMetadataService {
        configService.appFramework = framework
        ref = EmbraceMetadataService(
            lazy {
                EnvelopeResourceSourceImpl(
                    hostedSdkVersionInfo,
                    AppEnvironment.Environment.PROD,
                    buildInfo,
                    PackageVersionInfo(packageInfo),
                    framework,
                    fakeArchitecture,
                    DeviceImpl(
                        mockk(relaxed = true),
                        preferencesService,
                        fakeBackgroundWorker(),
                        SystemInfo(),
                        FakeEmbLogger()
                    ),
                    FakeRnBundleIdTracker()
                )
            },
            context,
            lazy { storageStatsManager },
            configService,
            preferencesService,
            fakeBackgroundWorker(),
            fakeClock,
            FakeEmbLogger()
        )
        if (precompute) {
            ref.precomputeValues()
        }
        return ref
    }

    @Test
    fun `test startup complete`() {
        every { preferencesService.installDate }.returns(null)
        getMetadataService()

        verify(exactly = 1) { preferencesService.appVersion = any() }
        verify(exactly = 1) { preferencesService.osVersion = any() }
        verify(exactly = 1) { preferencesService.installDate = any() }
    }

    @Test
    fun `test startup complete if it is not the first time`() {
        every { preferencesService.installDate }.returns(1234L)
        getMetadataService()
        verify(exactly = 0) { preferencesService.installDate = any() }
    }
}
