package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.os.Bundle
import android.os.Trace
import android.widget.TextView
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * Populates a session with telemetry and ends it, displaying a "done" outcome once the SDK's queued
 * persistence writes have had time to run. SessionBenchmark waits for that text and asserts on it.
 */
class BenchmarkActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "running" }
        setContentView(status)
    }

    override fun onResume() {
        super.onResume()
        val started = Embrace.isStarted
        trace("session-workload", ::populateSession)

        // end the current session and start a new one
        val previousSessionId = Embrace.currentUserSessionId
        trace("session-end", Embrace::endUserSession)

        val outcome = when {
            !started -> "$DONE sdk-not-started"
            Embrace.currentUserSessionId == previousSessionId -> "$DONE end-declined"
            else -> "$DONE ok"
        }

        // settle writes
        status.postDelayed({ status.text = outcome }, 2000L)
    }

    private fun populateSession() {
        Embrace.setUserIdentifier("benchmark-user")
        Embrace.addUserPersona("benchmark-persona")
        repeat(20) {
            Embrace.addUserSessionProperty("property-$it", "value-$it", PropertyScope.USER_SESSION)
            Embrace.addBreadcrumb("breadcrumb-$it")
        }
        repeat(10) { Embrace.startSpan("completed-span-$it").populate().stop() }
        repeat(5) { Embrace.startSpan("in-flight-span-$it").populate() }
    }

    private fun EmbraceSpan.populate(): EmbraceSpan = apply {
        repeat(10) { addAttribute("attribute-$it", "value-$it") }
        repeat(5) { addEvent("event-$it") }
    }

    private inline fun <T> trace(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    private companion object {
        const val DONE = "done:"
    }
}
