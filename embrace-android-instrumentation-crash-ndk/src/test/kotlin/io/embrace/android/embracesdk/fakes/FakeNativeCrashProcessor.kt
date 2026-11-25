package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

class FakeNativeCrashProcessor : NativeCrashProcessor {

    private val nativeCrashDataBlobs = mutableListOf<NativeCrashData>()
    private val deletedCrashIds = mutableSetOf<String>()

    override fun deleteAllNativeCrashes() {
        nativeCrashDataBlobs.mapTo(deletedCrashIds, NativeCrashData::nativeCrashId)
        nativeCrashDataBlobs.clear()
    }

    override fun deleteNativeCrash(nativeCrash: NativeCrashData) {
        deletedCrashIds.add(nativeCrash.nativeCrashId)
        nativeCrashDataBlobs.removeAll { it.nativeCrashId == nativeCrash.nativeCrashId }
    }

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashDataBlobs

    override fun loadNativeCrash(nativeCrash: NativeCrashData): NativeCrashData? =
        nativeCrashDataBlobs.firstOrNull { it.nativeCrashId == nativeCrash.nativeCrashId }
            ?: nativeCrash.takeUnless { it.nativeCrashId in deletedCrashIds }

    fun addNativeCrashData(data: NativeCrashData) {
        nativeCrashDataBlobs.add(data)
    }
}
