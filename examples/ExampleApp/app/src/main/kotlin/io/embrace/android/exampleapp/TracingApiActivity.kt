package io.embrace.android.exampleapp

import android.os.Trace
import androidx.activity.ComponentActivity
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TracingApiActivity : ComponentActivity() {
    private val attributesPairs = (1..ATTRIBUTES_PER_SPAN).map { Pair("test-key-$it", "test-long-ish-values-$it") }
    private val eventNames = (1..EVENTS_PER_SPAN).map { "test-span-event-name-$it" }
    private val extraAttributes = mapOf(attributesPairs.first().first to attributesPairs.first().second)
    private val embrace = Embrace.getInstance()

    override fun onResume() {
        super.onResume()
        val attributes = intent.getBooleanExtra("attributes", false)
        val events = intent.getBooleanExtra("events", false)
        val links = intent.getBooleanExtra("links", false)
        val totalSpans = intent.getIntExtra("totalSpans", DEFAULT_SPAN_COUNT)

        try {
            Trace.beginSection("start-and-stop-spans")
            createAndStartTrace(
                attributes = attributes,
                events = events,
                links = links,
                totalSpans = totalSpans,
            ).stopTrace()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Return a list of spans that belong to the same trace
     */
    private fun createAndStartTrace(
        attributes: Boolean,
        events: Boolean,
        links: Boolean,
        totalSpans: Int,
    ): List<EmbraceSpan> {
        val traceSpans = mutableListOf<EmbraceSpan>()
        val parent = createSpan(
            name = DEFAULT_ROOT_NAME,
            attributes = attributes,
            events = events,
            start = true
        ).apply {
            traceSpans.add(this)
        }
        repeat(totalSpans - 1) {
            traceSpans.add(
                createSpan(
                    name = DEFAULT_CHILD_NAME,
                    parent = parent,
                    attributes = attributes,
                    events = events,
                    links = links,
                    start = true
                )
            )
        }
        return traceSpans
    }

    private fun List<EmbraceSpan>.stopTrace() {
        forEach {
            if (it.parent != null) {
                it.wrappedStop()
            }
        }
        single { it.parent == null }.wrappedStop()
    }

    private fun EmbraceSpan.wrappedStop() {
        try {
            Trace.beginSection("stop-span")
            stop()
        } finally {
            Trace.endSection()
        }
    }

    private fun createSpan(
        name: String,
        parent: EmbraceSpan? = null,
        attributes: Boolean = false,
        events: Boolean = false,
        links: Boolean = false,
        start: Boolean = false,
    ): EmbraceSpan {
        val span = try {
            Trace.beginSection("create-span")
            checkNotNull(embrace.createSpan(name = name, parent = parent))
        } finally {
            Trace.endSection()
        }

        return span.apply {
            if (start) {
                try {
                    Trace.beginSection("start-span")
                    start()
                } finally {
                    Trace.endSection()
                }

            }
            try {
                Trace.beginSection("add-stuff")
                if (attributes) {
                    attributesPairs.forEach {
                        addAttribute(it.first, it.second)
                    }
                }

                if (events) {
                    eventNames.forEach {
                        addEvent(name = it, timestampMs = null, attributes = extraAttributes)
                    }
                }

                if (links && parent != null) {
                    repeat(LINKS_PER_SPAN) {
                        addLink(linkedSpan = parent, attributes = extraAttributes)
                    }
                }
            } finally {
                Trace.endSection()
            }
        }
    }

    companion object {
        private const val DEFAULT_SPAN_COUNT = 10
        private const val ATTRIBUTES_PER_SPAN = 20
        private const val EVENTS_PER_SPAN = 10
        private const val LINKS_PER_SPAN = 5
        private const val DEFAULT_ROOT_NAME = "parent"
        private const val DEFAULT_CHILD_NAME = "child"
    }
}

