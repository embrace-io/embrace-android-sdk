package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

internal class FilesDirApplication(private val filesDir: File) : Application() {
    private val appInfo = ApplicationInfo()

    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "com.fake.package"
    override fun getApplicationInfo(): ApplicationInfo = appInfo
    override fun getFilesDir(): File = filesDir
}
