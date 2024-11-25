package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.payload.NativeCrashData

class FakeNativeCrashProcessor : NativeCrashProcessor {

    private val nativeCrashDataBlobs = mutableListOf<NativeCrashData>()

    override fun deleteAllNativeCrashes() {
        nativeCrashDataBlobs.clear()
    }

    override fun getLatestNativeCrash(): NativeCrashData? = nativeCrashDataBlobs.lastOrNull()

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashDataBlobs

    fun addNativeCrashData(data: NativeCrashData) {
        nativeCrashDataBlobs.add(data)
    }
}
