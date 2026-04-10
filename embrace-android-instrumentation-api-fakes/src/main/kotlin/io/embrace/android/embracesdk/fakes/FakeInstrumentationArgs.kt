package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

class FakeInstrumentationArgs(
    override val application: Application,
    override val configService: FakeConfigService = FakeConfigService(),
    override val context: Context = application,
    override val destination: FakeTelemetryDestination = FakeTelemetryDestination(),
    override val logger: FakeInternalLogger = FakeInternalLogger(),
    override val clock: FakeClock = FakeClock(),
    override val store: FakeKeyValueStore = FakeKeyValueStore(),
    override val serializer: PlatformSerializer = TestPlatformSerializer(),
    override val ordinalStore: OrdinalStore = FakeOrdinalStore(),
    override val processIdentifier: String = "fake-process-id",
    override val appStateTracker: AppStateTracker = FakeAppStateTracker(),
    override val telemetryService: TelemetryService = FakeTelemetryService(),
    val backgroundWorkerSupplier: (worker: Worker.Background) -> BackgroundWorker = { fakeBackgroundWorker() },
    val priorityWorkerSupplier: (worker: Worker.Priority) -> PriorityWorker<*> = { fakePriorityWorker<Any>() },
    val sessionIdSupplier: () -> String? = { null },
    val sessionChangeListeners: MutableList<SessionPartChangeListener> = mutableListOf(),
    val sessionEndListeners: MutableList<SessionPartEndListener> = mutableListOf(),
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

    override fun userSessionProperties(): Map<String, String> = emptyMap()

    override val crashMarkerFile: File by lazy { File.createTempFile("crash_marker", "") }

    override val navigationTrackingService = FakeNavigationTrackingService()

    override fun registerSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun registerSessionPartEndListener(listener: SessionPartEndListener) {
        sessionEndListeners.add(listener)
    }
}
