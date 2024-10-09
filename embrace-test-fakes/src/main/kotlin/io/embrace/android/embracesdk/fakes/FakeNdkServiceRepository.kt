package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkServiceRepository
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class FakeNdkServiceRepository : NdkServiceRepository {
    private val nativeCrashFiles = ConcurrentLinkedQueue<File>()
    private val errorFiles = ConcurrentHashMap<File, File>()
    private val mapFiles = ConcurrentHashMap<File, File>()

    override fun sortNativeCrashes(byOldest: Boolean): List<File> = nativeCrashFiles.toList()

    override fun errorFileForCrash(crashFile: File): File? = errorFiles[crashFile]

    override fun mapFileForCrash(crashFile: File): File? = mapFiles[crashFile]

    override fun deleteFiles(crashFile: File, errorFile: File?, mapFile: File?, nativeCrash: NativeCrashData?) {
        nativeCrashFiles.remove(crashFile)
        errorFiles.remove(crashFile)
        mapFiles.remove(mapFile)
    }

    fun addCrashFiles(
        nativeCrashFile: File,
        errorFile: File? = null,
        mapFile: File? = null,
    ) {
        nativeCrashFiles.add(nativeCrashFile)
        errorFile?.run {
            errorFiles[nativeCrashFile] = this
        }
        mapFile?.run {
            mapFiles[nativeCrashFile] = this
        }
    }
}
