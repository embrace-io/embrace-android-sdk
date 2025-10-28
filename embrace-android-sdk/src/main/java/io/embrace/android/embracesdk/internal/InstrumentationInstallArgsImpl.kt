package io.embrace.android.embracesdk.internal

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.ConcurrentHashMap

internal class InstrumentationInstallArgsImpl(
    override val configService: ConfigService,
    override val sessionSpanWriter: SessionSpanWriter,
    override val logger: EmbLogger,
    override val clock: Clock,
    override val traceWriter: TraceWriter,
    override val context: Context,
    private val workerThreadModule: WorkerThreadModule,
) : InstrumentationInstallArgs {

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = workerThreadModule.backgroundWorker(worker)

    private val memo = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> systemService(name: String): T? {
        return memo.getOrPut(name) {
            context.getSystemServiceSafe<T>(name)
        } as? T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Context.getSystemServiceSafe(name: String): T? = runCatching {
        getSystemService(name)
    }.getOrNull() as T?
}
