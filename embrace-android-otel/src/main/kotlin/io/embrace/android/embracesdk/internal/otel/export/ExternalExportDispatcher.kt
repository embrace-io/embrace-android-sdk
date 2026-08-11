package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.internal.utils.EmbTrace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Runs export to customer-supplied OTel exporters away from the thread that produced the telemetry.
 *
 * A customer exporter is arbitrary suspending code - an OTLP exporter performs network I/O - so
 * exporting inline would block whichever thread ended a span or emitted a log.
 */
class ExternalExportDispatcher(
    dispatcherProvider: () -> CoroutineDispatcher = ::singleThreadedDispatcher,
) {

    private val lazyDispatcher = lazy(dispatcherProvider)
    private val lazyScope = lazy { CoroutineScope(SupervisorJob() + lazyDispatcher.value) }

    /**
     * Exports to every exporter in [exporters] on the export thread and returns immediately. Each
     * exporter is isolated: one that throws neither stops the others nor propagates into the app.
     */
    fun <E> dispatch(exporters: List<E>, export: suspend (E) -> Unit) {
        lazyScope.value.launch {
            EmbTrace.trace("otel-external-export") {
                exporters.forEach { exporter ->
                    try {
                        export(exporter)
                    } catch (ignored: Throwable) {
                    }
                }
            }
        }
    }

    /**
     * Suspends until everything dispatched before this call has finished exporting.
     */
    suspend fun awaitPendingExports() {
        if (!lazyScope.isInitialized()) {
            return
        }
        lazyScope.value.coroutineContext.job.children.toList().joinAll()
    }

    /**
     * Releases the export thread once the exports already queued have run.
     */
    fun shutdown() {
        if (!lazyDispatcher.isInitialized()) {
            return
        }
        (lazyDispatcher.value as? ExecutorCoroutineDispatcher)?.close()
    }

    private companion object {

        /**
         * Single-threaded so that telemetry reaches the customer in the order the SDK produced it.
         */
        fun singleThreadedDispatcher(): CoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Executors.defaultThreadFactory().newThread(runnable).apply {
                    name = "emb-otel-export"
                }
            }.asCoroutineDispatcher()
    }
}
