package io.embrace.android.embracesdk.fakes

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker

class FakeInstrumentationInstallArgs(
    override val application: Application,
    override val configService: FakeConfigService = FakeConfigService(),
    override val context: Context = application,
    override val destination: FakeTelemetryDestination = FakeTelemetryDestination(),
    override val logger: FakeEmbLogger = FakeEmbLogger(),
    override val clock: FakeClock = FakeClock(),
    override val store: FakeKeyValueStore = FakeKeyValueStore(),
) : InstrumentationInstallArgs {

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker {
        throw UnsupportedOperationException()
    }

    override fun <T> systemService(name: String): T? {
        throw UnsupportedOperationException()
    }
}
