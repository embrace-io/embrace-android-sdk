package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker

public fun fakeBackgroundWorker(): BackgroundWorker = BackgroundWorker(BlockableExecutorService())
public fun fakeScheduledWorker(): ScheduledWorker = ScheduledWorker(BlockingScheduledExecutorService(blockingMode = false))
