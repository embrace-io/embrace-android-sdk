package io.embrace.android.embracesdk.fakes

import android.content.Context
import io.embrace.android.embracesdk.internal.injection.createSdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageImpl
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * The directories the SDK persists telemetry in.
 */
val TELEMETRY_DIRS: List<SdkDir> = listOf(
    SdkDir.TELEMETRY_READY,
    SdkDir.TELEMETRY_PENDING_SNAPSHOTS,
    SdkDir.TELEMETRY_PENDING_NATIVE_CRASHES,
    SdkDir.TELEMETRY_LOG_ENVELOPES,
    SdkDir.TELEMETRY_PENDING_SESSION_PARTS,
)

fun testSdkStorage(context: Context): SdkStorage = createSdkStorage(context, FakeErrorHandler())

fun testSdkStorage(root: File): SdkStorage = SdkStorageImpl(FileSystem.SYSTEM, root.toOkioPath(), FakeErrorHandler())
