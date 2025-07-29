package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun fakeBackgroundWorker(): BackgroundWorker = BackgroundWorker(BlockingScheduledExecutorService(blockingMode = false))
