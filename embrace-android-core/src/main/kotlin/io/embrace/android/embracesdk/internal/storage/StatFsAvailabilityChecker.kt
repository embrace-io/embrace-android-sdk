package io.embrace.android.embracesdk.internal.storage

import android.content.Context
import android.os.StatFs

internal class StatFsAvailabilityChecker(
    context: Context,
) : StorageAvailabilityChecker {
    private val statFs: StatFs by lazy {
        StatFs(context.filesDir.path)
    }

    override fun getAvailableBytes(): Long {
        return statFs.availableBytes
    }
}
