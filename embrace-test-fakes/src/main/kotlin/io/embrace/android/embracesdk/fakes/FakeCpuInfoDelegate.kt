package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate

class FakeCpuInfoDelegate(
    private val cpuName: String? = "fake_cpu",
    private val egl: String = "fake_egl",
) : CpuInfoDelegate {
    override fun getCpuName(): String? = cpuName

    override fun getEgl(): String = egl
}
