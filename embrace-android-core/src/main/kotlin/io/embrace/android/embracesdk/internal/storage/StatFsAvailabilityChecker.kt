package io.embrace.android.embracesdk.internal.storage

import android.os.StatFs
import java.io.File

/**
 * Reports the bytes available on the volume that holds [dir].
 */
class StatFsAvailabilityChecker(
    dir: Lazy<File>,
) : StorageAvailabilityChecker {

    private val statFs: StatFs by lazy {
        StatFs(dir.value.path)
    }

    override fun getAvailableBytes(): Long {
        return statFs.availableBytes
    }
}
