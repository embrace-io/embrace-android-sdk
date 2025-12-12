package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

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
    override val symbols: Map<String, String>? = emptyMap(),
    override val appStateTracker: AppStateTracker = FakeAppStateTracker(),
    val backgroundWorkerSupplier: (worker: Worker.Background) -> BackgroundWorker = { fakeBackgroundWorker() },
    val priorityWorkerSupplier: (worker: Worker.Priority) -> PriorityWorker<*> = { fakePriorityWorker<Any>() },
    val sessionIdSupplier: () -> String? = { null },
    val sessionChangeListeners: MutableList<SessionChangeListener> = mutableListOf(),
    val sessionEndListeners: MutableList<SessionEndListener> = mutableListOf(),
    val systemServiceSupplier: (name: String) -> Any? = { null },
) : InstrumentationArgs {

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = backgroundWorkerSupplier(worker)

    @Suppress("UNCHECKED_CAST")
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> {
        return priorityWorkerSupplier(worker) as PriorityWorker<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> systemService(name: String): T? = systemServiceSupplier(name) as? T

    override fun sessionId(): String? = sessionIdSupplier()

    override fun sessionProperties(): Map<String, String> = emptyMap()

    override val crashMarkerFile: File by lazy { File.createTempFile("crash_marker", "") }

    override fun registerSessionChangeListener(listener: SessionChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun registerSessionEndListener(listener: SessionEndListener) {
        sessionEndListeners.add(listener)
    }
}
