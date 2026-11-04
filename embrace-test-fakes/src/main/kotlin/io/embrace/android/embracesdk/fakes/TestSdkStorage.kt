package io.embrace.android.embracesdk.fakes

import android.content.Context
import io.embrace.android.embracesdk.internal.injection.createLegacySdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageImpl
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

fun testSdkStorage(context: Context): SdkStorage =
    createLegacySdkStorage(context.filesDir, context::getCacheDir, FakeErrorHandler())

fun testSdkStorage(root: File): SdkStorage = SdkStorageImpl(FileSystem.SYSTEM, root.toOkioPath(), FakeErrorHandler())
