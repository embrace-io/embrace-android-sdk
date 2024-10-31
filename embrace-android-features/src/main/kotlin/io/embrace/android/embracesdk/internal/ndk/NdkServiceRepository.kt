package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import java.io.File

/**
 * Interface to interact with the data files persisted during a native crash
 */
interface NdkServiceRepository {

    /**
     * Return a list of the raw files of native crashes recorded by the SDK in the given sort order
     */
    fun sortNativeCrashes(byOldest: Boolean): List<File>

    /**
     * Return the [File] containing errors during the processing of a given native crash
     */
    fun errorFileForCrash(crashFile: File): File?

    /**
     * Return the [File] containing process mapping info for a given native crash
     */
    fun mapFileForCrash(crashFile: File): File?

    /**
     * Delete specific files used to store data about native crashes
     */
    fun deleteFiles(
        crashFile: File,
        errorFile: File?,
        mapFile: File?,
        nativeCrash: NativeCrashData?,
    )
}
