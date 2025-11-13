package io.embrace.android.embracesdk.fakes.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.fakes.system.mockApplication
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

/**
 * If used in a Robolectric test, [application] and [context] will be fakes supplied by the Robolectric framework
 */
class FakeCoreModule(
    override val application: Application =
        if (RuntimeEnvironment.getApplication() == null) mockApplication() else RuntimeEnvironment.getApplication(),
    override val context: Context =
        if (isMockKMock(application)) getMockedContext() else application.applicationContext,
    override val serviceRegistry: ServiceRegistry = ServiceRegistry(),
    override val appEnvironment: AppEnvironment = AppEnvironment(true),
    override val buildInfo: BuildInfo = BuildInfo(
        "fakeBuildId",
        "fakeBuildType",
        "fakeBuildFlavor",
        "fakeRnBundleId",
    ),
    override val packageVersionInfo: PackageVersionInfo = fakePackageVersionInfo,
    override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A,
) : CoreModule {

    companion object {

        @Suppress("DEPRECATION")
        private val fakePackageInfo = PackageInfo().apply {
            packageName = "com.fake.package"
            versionName = "2.5.1"
            versionCode = 99
        }

        private val fakePackageVersionInfo = PackageVersionInfo(fakePackageInfo)

        fun getMockedContext(): Context {
            val mockContext = mockk<Context>(relaxed = true)
            every { mockContext.packageName }.returns(fakePackageVersionInfo.packageName)
            every { mockContext.packageManager.getPackageInfo(fakePackageVersionInfo.packageName, 0) }.returns(
                fakePackageInfo
            )
            return mockContext
        }
    }
}
