package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val SHUTDOWN_TIMEOUT_MS = 5_000L

/**
 * Runs export to customer-supplied OTel exporters away from the thread that produced the telemetry.
 *
 * A customer exporter is arbitrary suspending code - an OTLP exporter performs network I/O - so
 * exporting inline would block whichever thread ended a span or emitted a log.
 */
class ExternalExportDispatcher(
    private val errorHandler: ErrorHandler,
    private val shutdownTimeoutMs: Long = SHUTDOWN_TIMEOUT_MS,
    dispatcherProvider: (() -> CoroutineDispatcher)? = null,
) {

    @Volatile
    private var exportThread: Thread? = null
    private val lazyDispatcher = lazy { dispatcherProvider?.invoke() ?: singleThreadedDispatcher() }
    private val job = SupervisorJob()
    private val lazyScope = lazy {
        val handler = CoroutineExceptionHandler { _, exc -> onUncaught(exc) }
        CoroutineScope(job + lazyDispatcher.value + handler)
    }

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
     * Releases the export thread once the exports already dispatched have finished, including any that suspend.
     * Exports still running after [shutdownTimeoutMs] are cancelled and the export thread is interrupted, so that a
     * hanging customer exporter cannot keep it alive.
     */
    fun shutdown() {
        job.complete()
        if (!lazyDispatcher.isInitialized()) {
            return
        }
        val dispatcher = lazyDispatcher.value as? ExecutorCoroutineDispatcher ?: return
        val finished = CountDownLatch(1)
        job.invokeOnCompletion {
            dispatcher.close()
            finished.countDown()
        }
        if (job.isCompleted) {
            return
        }
        thread(name = "emb-otel-export-shutdown", isDaemon = true) {
            if (!finished.await(shutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
                job.cancel()
                dispatcher.close()
                exportThread?.interrupt()
            }
        }
    }

    private fun onUncaught(exc: Throwable) {
        if (exc !is IOException && exc !is VirtualMachineError) {
            errorHandler.trackSdkError(SdkErrorType.OtelExternalExportFail, exc)
        }
    }

    /**
     * Single-threaded so that telemetry reaches the customer in the order the SDK produced it.
     */
    private fun singleThreadedDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Executors.defaultThreadFactory().newThread(runnable).apply {
                name = "emb-otel-export"
                isDaemon = true
                exportThread = this
            }
        }.asCoroutineDispatcher()
}
