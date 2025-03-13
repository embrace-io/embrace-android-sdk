package io.embrace.android.embracesdk.fakes

import android.os.Handler
import android.os.Looper
import android.os.Message
import io.embrace.android.embracesdk.internal.handler.MainThreadHandler
import java.util.LinkedList
import java.util.Queue

class FakeMainThreadHandler(
    private val handlerProvider: () -> Handler = ::defaultHandlerProvider
) : MainThreadHandler {

    override val wrappedHandler: Handler
        get() = handlerProvider()

    val messageQueue: Queue<Message> = LinkedList()

    override fun postAtFrontOfQueue(function: () -> Unit) {
        function()
    }

    override fun postDelayed(runnable: Runnable, delayMillis: Long) {
        Thread.sleep(20L)
        runnable.run()
    }

    override fun sendMessageAtFrontOfQueue(message: Message): Boolean {
        messageQueue.add(message)
        return true
    }
}

private fun defaultHandlerProvider() = Handler(checkNotNull(Looper.getMainLooper()))
