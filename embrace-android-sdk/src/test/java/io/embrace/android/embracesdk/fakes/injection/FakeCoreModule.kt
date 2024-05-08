package io.embrace.android.embracesdk.fakes.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.fakes.FakeAndroidResourcesService
import io.embrace.android.embracesdk.fakes.system.mockApplication
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.registry.ServiceRegistry
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

/**
 * If used in a Robolectric test, [application] and [context] will be fakes supplied by the Robolectric framework
 */
internal class FakeCoreModule(
    val logger: InternalEmbraceLogger = InternalEmbraceLogger(),
    override val application: Application =
        if (RuntimeEnvironment.getApplication() == null) mockApplication() else RuntimeEnvironment.getApplication(),
    override val context: Context =
        if (isMockKMock(application)) getMockedContext() else application.applicationContext,
    override val appFramework: AppFramework = AppFramework.NATIVE,
    override val serviceRegistry: ServiceRegistry = ServiceRegistry(logger),
    override val jsonSerializer: EmbraceSerializer = EmbraceSerializer(),
    override val resources: FakeAndroidResourcesService = FakeAndroidResourcesService(),
    override val isDebug: Boolean = if (isMockKMock(context)) false else AppEnvironment(context.applicationInfo).isDebug,
    override val buildInfo: BuildInfo = BuildInfo.fromResources(resources, context.packageName),
    @Suppress("DEPRECATION")
    override val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
) : CoreModule {

    companion object {

        @Suppress("DEPRECATION")
        fun getMockedContext(): Context {
            val packageInfo = PackageInfo()
            packageInfo.versionName = "1.0.0"
            packageInfo.versionCode = 10

            val mockContext = mockk<Context>(relaxed = true)
            every { mockContext.packageName }.returns("package-info")
            every { mockContext.packageManager.getPackageInfo("package-info", 0) }.returns(packageInfo)
            return mockContext
        }
    }
}
