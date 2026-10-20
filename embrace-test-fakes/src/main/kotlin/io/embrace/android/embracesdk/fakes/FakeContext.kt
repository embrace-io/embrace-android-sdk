package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockk
import org.robolectric.RuntimeEnvironment

private const val FAKE_PACKAGE_NAME = "com.fake.package"

@Suppress("DEPRECATION")
private val fakePackageInfo = PackageInfo().apply {
    packageName = FAKE_PACKAGE_NAME
    versionName = "2.5.1"
    versionCode = 99
}

/**
 * The Robolectric application when running under Robolectric, otherwise a relaxed mock.
 */
fun fakeApplication(): Application = RuntimeEnvironment.getApplication() ?: mockk(relaxed = true) {
    every { registerActivityLifecycleCallbacks(any()) } returns Unit
}

/**
 * The application context of [application], or a mocked context with fake package info if [application] is a mock.
 */
fun fakeContext(application: Application = fakeApplication()): Context = if (isMockKMock(application)) {
    mockk<Context>(relaxed = true).apply {
        every { packageName }.returns(FAKE_PACKAGE_NAME)
        every { packageManager.getPackageInfo(FAKE_PACKAGE_NAME, 0) }.returns(fakePackageInfo)
    }
} else {
    application.applicationContext
}
