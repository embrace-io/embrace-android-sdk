package io.embrace.android.embracesdk.fakes.injection

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

/**
 * If used in a Robolectric test, [application] and [context] will be fakes supplied by the Robolectric framework
 */
class FakeCoreModule(
    val application: Application =
        if (RuntimeEnvironment.getApplication() == null) mockk(relaxed = true) {
            every { registerActivityLifecycleCallbacks(any()) } returns Unit
        } else RuntimeEnvironment.getApplication(),
    val context: Context =
        if (isMockKMock(application)) getMockedContext() else application.applicationContext,
    val store: KeyValueStore = FakeKeyValueStore(),
    val ordinalStore: OrdinalStore = FakeOrdinalStore(),
    val appVersionStartupCounter: Int = 1,
    val eventBus: EventBus = EventBus(FakeErrorHandler()),
) {

    companion object {

        @Suppress("DEPRECATION")
        private val fakePackageInfo = PackageInfo().apply {
            packageName = "com.fake.package"
            versionName = "2.5.1"
            versionCode = 99
        }

        private val fakeBuildInfo = BuildInfo(
            buildId = "fakeBuildId",
            buildType = "fakeBuildType",
            buildFlavor = "fakeBuildFlavor",
            rnBundleId = "fakeRnBundleId",
            versionName = "2.5.1",
            versionCode = "99",
            packageName = "com.fake.package",
        )

        fun getMockedContext(): Context {
            val mockContext = mockk<Context>(relaxed = true)
            val packageName = fakeBuildInfo.packageName ?: "com.fake.package"
            every { mockContext.packageName }.returns(packageName)
            every { mockContext.packageManager.getPackageInfo(packageName, 0) }.returns(
                fakePackageInfo
            )
            return mockContext
        }
    }
}
