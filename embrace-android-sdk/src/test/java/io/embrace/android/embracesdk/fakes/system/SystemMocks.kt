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

public fun mockLooper(): Looper = mockk(relaxed = true) {
    every { thread } returns Thread.currentThread()
}

public fun mockMessageQueue(): MessageQueue = mockk(relaxed = true)
public fun mockMessage(): Message = mockk(relaxed = true)
public fun mockActivity(): Activity = mockk(relaxed = true) {
    every { localClassName } returns "MyMockActivity"
}

public fun mockIntent(): Intent = mockk(relaxed = true)
public fun mockContext(): Context = mockk(relaxed = true)
public fun mockApplication(): Application = mockk(relaxed = true) {
    every { registerActivityLifecycleCallbacks(any()) } returns Unit
}

public fun mockResources(): Resources = mockk(relaxed = true)
public fun mockBundle(): Bundle = mockk(relaxed = true)
public fun mockStorageStatsManager(): StorageStatsManager = mockk(relaxed = true)
public fun mockWindowManager(): WindowManager = mockk(relaxed = true)
public fun mockPowerManager(): PowerManager = mockk(relaxed = true)
