package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.PropertyScope

/**
 * App instances that die before their session part ends, as a crash or the system killing the app would leave
 * it, for the next launch to resurrect the part. A resurrection benchmark records what one leaves on disk as its
 * fixture. They differ in how much the part holds, from a short session to one filled to the SDK's default
 * limits per session part, since what resurrecting a part costs grows with its size. Dead parts rarely pile up:
 * the next launch resurrects them early, so several only build up when launches die before it can, and those
 * parts are small.
 */
object ResurrectionScenarios {

    val smallPart: ScenarioSpec = deadSessionPart(
        id = "small_part",
        contents = PartContents(
            requests = 10,
            breadcrumbs = 5,
            completedSpans = 5,
            inFlightSpans = 1,
            sessionProperties = 2,
        ),
    )

    val mediumPart: ScenarioSpec = deadSessionPart(
        id = "medium_part",
        contents = PartContents(
            requests = 100,
            breadcrumbs = 25,
            completedSpans = 40,
            inFlightSpans = 5,
            sessionProperties = 5,
        ),
    )

    val largePart: ScenarioSpec = deadSessionPart(
        id = "large_part",
        contents = PartContents(
            requests = 500,
            breadcrumbs = 60,
            completedSpans = 150,
            inFlightSpans = 15,
            sessionProperties = 20,
        ),
    )

    // the SDK's default limits per session part: 500 spans created through its API, whether ended or not, 2000
    // network requests, at most 1000 to a domain, 100 breadcrumbs and 100 session properties
    val cappedPart: ScenarioSpec = deadSessionPart(
        id = "capped_part",
        contents = PartContents(
            requests = 2000,
            breadcrumbs = 100,
            completedSpans = 450,
            inFlightSpans = 50,
            sessionProperties = 100,
        ),
    )

    val all: List<ScenarioSpec> = listOf(
        smallPart,
        mediumPart,
        largePart,
        cappedPart,
    )

    fun byId(id: String): ScenarioSpec =
        all.firstOrNull { it.id == id } ?: error("No such scenario: $id")
}

/**
 * How much a dead session part holds. Its [inFlightSpans] are still running when the app instance dies.
 */
internal class PartContents(
    val requests: Int,
    val breadcrumbs: Int,
    val completedSpans: Int,
    val inFlightSpans: Int,
    val sessionProperties: Int,
)

/**
 * A scenario that fills a session part with [contents] and leaves it unfinished. Its requests and completed spans
 * have just ended when they are recorded, each after a duration of its own, so that recording the part takes no
 * real time, and no two put the same numbers in the payload, which then compresses no better than a real app's.
 */
internal fun deadSessionPart(id: String, contents: PartContents): ScenarioSpec = ScenarioSpec(
    id = id,
    description = "${contents.requests} requests, ${contents.completedSpans} completed spans, " +
        "${contents.breadcrumbs} breadcrumbs and ${contents.sessionProperties} session properties, then " +
        "${contents.inFlightSpans} spans still running when the app instance dies",
) {
    repeat(contents.sessionProperties) { index ->
        embrace.addUserSessionProperty("benchmark.property.$index", "value-$index", PropertyScope.USER_SESSION)
    }
    repeat(contents.requests) { request ->
        val endMs = nowMs
        recordRequest(
            url = "https://${REQUEST_DOMAINS[request % REQUEST_DOMAINS.size]}/v2/items/${request % 40}?page=$request",
            startTimeMs = endMs - durationMs(request),
            endTimeMs = endMs,
            bytesReceived = 2_048L + request,
        )
    }
    repeat(contents.completedSpans) { span ->
        val endMs = nowMs
        val startMs = endMs - durationMs(span)
        embrace.startSpan("item-load", startTimeMs = startMs).apply {
            spanAttributes(span).forEach { (key, value) -> addAttribute(key, value) }
            addEvent("item-fetched", startMs + durationMs(span) / 2, emptyMap())
            stop(endTimeMs = endMs)
        }
    }
    repeat(contents.breadcrumbs) { breadcrumb ->
        embrace.addBreadcrumb("user tapped item $breadcrumb")
    }

    // the uploads still running when the app instance dies, which it leaves behind as span snapshots
    repeat(contents.inFlightSpans) { upload ->
        embrace.startSpan("upload-photo", startTimeMs = nowMs).apply {
            spanAttributes(upload).forEach { (key, value) -> addAttribute(key, value) }
            addEvent("upload-started", nowMs, emptyMap())
        }
    }
}

private fun spanAttributes(index: Int): Map<String, String> =
    (0 until SPAN_ATTRIBUTES).associate { "benchmark.attribute.$it" to "value-$index-$it" }

// distinct for the first DURATION_SPREAD_MS items, since the multiplier and the spread are coprime
private fun durationMs(index: Int): Long = MIN_DURATION_MS + index * DURATION_STEP_MS % DURATION_SPREAD_MS

// a domain takes at most 1000 requests a session part, so the capped part's 2000 need more than two
private val REQUEST_DOMAINS = listOf("api.example.com", "cdn.example.com", "img.example.com", "auth.example.com")

private const val SPAN_ATTRIBUTES = 5
private const val MIN_DURATION_MS = 40L
private const val DURATION_STEP_MS = 37L
private const val DURATION_SPREAD_MS = 211L
