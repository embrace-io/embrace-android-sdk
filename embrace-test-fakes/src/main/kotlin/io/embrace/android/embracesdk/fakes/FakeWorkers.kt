package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PrioritizedWorker
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker

fun fakePrioritizedWorker(): PrioritizedWorker = PrioritizedWorker(BlockableExecutorService())
fun fakeBackgroundWorker(): BackgroundWorker = BackgroundWorker(BlockableExecutorService())
fun fakeScheduledWorker(): ScheduledWorker = ScheduledWorker(BlockingScheduledExecutorService(blockingMode = false))
