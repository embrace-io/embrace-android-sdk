package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import org.robolectric.RuntimeEnvironment

private const val FAKE_PACKAGE_NAME = "com.fake.package"

/**
 * An [Application] for plain JVM tests. Unoverridden framework methods return default values.
 */
private class FakeJvmApplication : Application() {
    private val appInfo = ApplicationInfo()

    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = FAKE_PACKAGE_NAME
    override fun getApplicationInfo(): ApplicationInfo = appInfo
}

/**
 * The Robolectric application when running under Robolectric, otherwise a fake application.
 */
fun fakeApplication(): Application = RuntimeEnvironment.getApplication() ?: FakeJvmApplication()

/**
 * The application context of [application].
 */
fun fakeContext(application: Application = fakeApplication()): Context = application.applicationContext
