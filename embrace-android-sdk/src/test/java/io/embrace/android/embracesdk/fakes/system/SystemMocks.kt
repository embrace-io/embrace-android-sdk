package io.embrace.android.embracesdk.fakes.system

import android.app.Activity
import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.os.PowerManager
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk

internal fun mockLooper(): Looper = mockk(relaxed = true) {
    every { thread } returns Thread.currentThread()
}

internal fun mockMessageQueue(): MessageQueue = mockk(relaxed = true)
internal fun mockMessage(): Message = mockk(relaxed = true)
internal fun mockActivity(): Activity = mockk(relaxed = true) {
    every { localClassName } returns "MyMockActivity"
}
internal fun mockIntent(): Intent = mockk(relaxed = true)
internal fun mockContext(): Context = mockk(relaxed = true)
internal fun mockApplication(): Application = mockk(relaxed = true) {
    every { registerActivityLifecycleCallbacks(any()) } returns Unit
}

internal fun mockResources(): Resources = mockk(relaxed = true)
internal fun mockBundle(): Bundle = mockk(relaxed = true)
internal fun mockStorageStatsManager(): StorageStatsManager = mockk(relaxed = true)
internal fun mockWindowManager(): WindowManager = mockk(relaxed = true)
internal fun mockPowerManager(): PowerManager = mockk(relaxed = true)
