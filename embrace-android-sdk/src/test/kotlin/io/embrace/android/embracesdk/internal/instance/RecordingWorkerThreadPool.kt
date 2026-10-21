package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPoolImpl
import java.util.concurrent.CountDownLatch

/**
 * A [WorkerThreadPool] that records when it has been closed.
 */
internal class RecordingWorkerThreadPool(
    private val base: WorkerThreadPool = WorkerThreadPoolImpl(FakeErrorHandler()),
) : WorkerThreadPool by base {

    val closed: CountDownLatch = CountDownLatch(1)

    override fun close() {
        base.close()
        closed.countDown()
    }
}
