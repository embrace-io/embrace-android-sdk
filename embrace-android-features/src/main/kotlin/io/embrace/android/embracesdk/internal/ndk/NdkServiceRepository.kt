package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import java.io.File

interface NdkServiceRepository {
    fun sortNativeCrashes(byOldest: Boolean): List<File>

    fun errorFileForCrash(crashFile: File): File?

    fun mapFileForCrash(crashFile: File): File?

    fun deleteFiles(
        crashFile: File,
        errorFile: File?,
        mapFile: File?,
        nativeCrash: NativeCrashData?
    )
}
