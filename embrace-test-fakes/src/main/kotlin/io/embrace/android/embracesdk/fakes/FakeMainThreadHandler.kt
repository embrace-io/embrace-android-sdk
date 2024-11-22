package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.handler.MainThreadHandler

class FakeMainThreadHandler : MainThreadHandler {
    override fun postAtFrontOfQueue(function: () -> Unit) {
        function()
    }

    override fun postDelayed(runnable: Runnable, delayMillis: Long) {
        Thread.sleep(20L)
        runnable.run()
    }
}
