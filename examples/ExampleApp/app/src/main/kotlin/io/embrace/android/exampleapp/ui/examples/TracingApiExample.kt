package io.embrace.android.exampleapp.ui.examples

import android.os.SystemClock
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.embrace.android.embracesdk.Embrace

private val clock = TimingClock()

@Composable
fun TracingApiExample() {
    Button(onClick = {
        Embrace.getInstance().recordSpan("calculate-fibonacci") {
            fibonacci(20)
        }
    }) {
        Text("Record span")
    }

    Button(onClick = {
        val startMs = clock.now()
        // do some work
        Thread.sleep(50)
        val endMs = clock.now()
        Embrace.getInstance().recordCompletedSpan("sleep", startMs, endMs)
    }) {
        Text("Record completed span")
    }

    Button(onClick = {
        val span = Embrace.getInstance().startSpan("my-span") ?: return@Button
        val childSpan = Embrace.getInstance().startSpan("my-subspan", span) ?: return@Button

        // Do some work
        Thread.sleep(50)
        childSpan.addEvent("my-event")
        span.addAttribute("my-attribute", "my-value")

        // end this span and its children
        span.stop()
    }) {
        Text("Span hierarchy")
    }
}

private fun fibonacci(n: Int): Int {
    return if (n <= 1) {
        n
    } else {
        fibonacci(n - 1) + fibonacci(n - 2)
    }
}

private class TimingClock {
    private val baseline = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun now(): Long = baseline + SystemClock.elapsedRealtime()
}
