package io.embrace.android.embracesdk.fakes.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.fakes.FakeAndroidResourcesService
import io.embrace.android.embracesdk.fakes.system.mockApplication
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.PackageVersionInfo
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

/**
 * If used in a Robolectric test, [application] and [context] will be fakes supplied by the Robolectric framework
 */
public class FakeCoreModule(
    private val logger: EmbLogger = EmbLoggerImpl(),
    override val application: Application =
        if (RuntimeEnvironment.getApplication() == null) mockApplication() else RuntimeEnvironment.getApplication(),
    override val context: Context =
        if (isMockKMock(application)) getMockedContext() else application.applicationContext,
    override val serviceRegistry: ServiceRegistry = ServiceRegistry(logger),
    override val resources: FakeAndroidResourcesService = FakeAndroidResourcesService(),
    override val isDebug: Boolean = if (isMockKMock(context)) false else AppEnvironment(context.applicationInfo).isDebug,
    override val buildInfo: BuildInfo = BuildInfo.fromResources(resources, context.packageName),
    override val packageVersionInfo: PackageVersionInfo = fakePackageVersionInfo
) : CoreModule {

    public companion object {

        @Suppress("DEPRECATION")
        private val fakePackageInfo = PackageInfo().apply {
            packageName = "com.fake.package"
            versionName = "2.5.1"
            versionCode = 99
        }

        @Suppress("DEPRECATION")
        private val fakePackageVersionInfo = PackageVersionInfo(fakePackageInfo)

        public fun getMockedContext(): Context {
            val mockContext = mockk<Context>(relaxed = true)
            every { mockContext.packageName }.returns(fakePackageVersionInfo.packageName)
            every { mockContext.packageManager.getPackageInfo(fakePackageVersionInfo.packageName, 0) }.returns(fakePackageInfo)
            return mockContext
        }
    }
}
