package io.embrace.android.embracesdk.internal.anr.ndk

import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrSample

class NativeThreadSamplerNdkDelegate : EmbraceNativeThreadSamplerService.NdkDelegate {
    external override fun setupNativeThreadSampler(is32Bit: Boolean): Boolean
    external override fun monitorCurrentThread(): Boolean
    external override fun startSampling(unwinderOrdinal: Int, intervalMs: Long)
    external override fun finishSampling(): List<NativeThreadAnrSample>?
}
