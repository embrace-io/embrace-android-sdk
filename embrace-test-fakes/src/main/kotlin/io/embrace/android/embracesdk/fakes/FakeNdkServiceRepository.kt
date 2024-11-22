package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NdkServiceRepository
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class FakeNdkServiceRepository : NdkServiceRepository {
    private val nativeCrashFiles = ConcurrentLinkedQueue<File>()

    override fun sortNativeCrashes(byOldest: Boolean): List<File> = nativeCrashFiles.toList()

    override fun deleteFiles(crashFile: File) {
        nativeCrashFiles.remove(crashFile)
    }

    fun addCrashFiles(nativeCrashFile: File) {
        nativeCrashFiles.add(nativeCrashFile)
    }

    override fun cleanOldCrashFiles() {
        // do nothing
    }
}
