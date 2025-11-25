package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker

class FakeInstrumentationArgs(
    override val application: Application,
    override val configService: FakeConfigService = FakeConfigService(),
    override val context: Context = application,
    override val destination: FakeTelemetryDestination = FakeTelemetryDestination(),
    override val logger: FakeEmbLogger = FakeEmbLogger(),
    override val clock: FakeClock = FakeClock(),
    override val store: FakeKeyValueStore = FakeKeyValueStore(),
    override val serializer: PlatformSerializer = TestPlatformSerializer(),
    override val ordinalStore: OrdinalStore = FakeOrdinalStore(),
    override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A,
    override val processIdentifier: String = "fake-process-id",
    val backgroundWorkerSupplier: (worker: Worker.Background) -> BackgroundWorker = { fakeBackgroundWorker() },
    val priorityWorkerSupplier: (worker: Worker.Priority) -> PriorityWorker<*> = { fakePriorityWorker<Any>() },
    val sessionIdSupplier: () -> String? = { null },
) : InstrumentationArgs {

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = backgroundWorkerSupplier(worker)

    @Suppress("UNCHECKED_CAST")
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> {
        return priorityWorkerSupplier(worker) as PriorityWorker<T>
    }

    override fun <T> systemService(name: String): T? {
        throw UnsupportedOperationException()
    }

    override fun sessionId(): String? = sessionIdSupplier()

    override fun sessionProperties(): Map<String, String> = emptyMap()
}
