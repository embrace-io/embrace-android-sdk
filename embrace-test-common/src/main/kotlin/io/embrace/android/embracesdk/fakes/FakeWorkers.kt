package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker

fun fakeBackgroundWorker(): BackgroundWorker = BackgroundWorker(BlockingScheduledExecutorService(blockingMode = false))
fun <T> fakePriorityWorker(): PriorityWorker<T> = PriorityWorker(BlockingScheduledExecutorService(blockingMode = false))
