package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.ScheduledWorker

internal fun fakeBackgroundWorker() = BackgroundWorker(BlockableExecutorService())
internal fun fakeScheduledWorker() = ScheduledWorker(BlockingScheduledExecutorService(blockingMode = false))
