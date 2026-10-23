package io.embrace.android.embracesdk.internal.instrumentation.aei

import java.io.InputStream

internal class FakeAeiRecord(
    override var timestamp: Long = 0,
    override var pid: Int = 0,
    override var processStateSummary: ByteArray? = null,
    override var importance: Int = 0,
    override var pss: Long = 0,
    override var rss: Long = 0,
    override var reason: Int = 0,
    override var status: Int = 0,
    override var description: String? = null,
    var traceProvider: () -> InputStream? = { null },
) : AeiRecord {
    override val traceInputStream: InputStream?
        get() = traceProvider()
}
