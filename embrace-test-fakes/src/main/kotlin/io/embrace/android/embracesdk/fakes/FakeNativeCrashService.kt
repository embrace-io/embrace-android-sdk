package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.opentelemetry.api.common.AttributeKey
import java.util.concurrent.ConcurrentLinkedQueue

class FakeNativeCrashService : NativeCrashService {

    val nativeCrashesSent = ConcurrentLinkedQueue<Pair<NativeCrashData, Map<String, String>>>()
    private val nativeCrashDataBlobs = mutableListOf<Pair<NativeCrashData, Map<String, String>>>()
    var checkAndSendNativeCrashInvocation: Int = 0

    override fun getAndSendNativeCrash(): NativeCrashData? {
        checkAndSendNativeCrashInvocation++
        return nativeCrashDataBlobs.lastOrNull()?.first
    }

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashDataBlobs.map { it.first }

    override fun sendNativeCrash(
        nativeCrash: NativeCrashData,
        sessionProperties: Map<String, String>,
        metadata: Map<AttributeKey<String>, String>,
    ) {
        nativeCrashesSent.add(Pair(nativeCrash, metadata.mapKeys { it.value } + sessionProperties))
    }

    override fun deleteAllNativeCrashes() {
        nativeCrashDataBlobs.clear()
    }

    fun addNativeCrashData(nativeCrashData: NativeCrashData, metadata: Map<String, String> = emptyMap()) {
        nativeCrashDataBlobs.add(Pair(nativeCrashData, metadata))
    }
}
