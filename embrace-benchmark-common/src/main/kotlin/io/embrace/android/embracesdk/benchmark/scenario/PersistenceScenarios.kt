package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.io.IOException

/**
 * Sessions a real user of an instrumented app would produce.
 */
object PersistenceScenarios {

    val quickCheck: ScenarioSpec = ScenarioSpec(
        id = "quick_check",
        description = "12s glance at a single screen after opening from a notification",
    ) {
        embrace.addBreadcrumb("opened from notification")
        embrace.recordSpan("notification-target-load") {
            request("https://api.example.com/v1/notifications/4821")
            advanceTime(180)
        }
        advanceTime(11_000)
    }

    val launchFanout: ScenarioSpec = ScenarioSpec(
        id = "launch_fanout",
        description = "startup fan-out of 16 requests in 2s, then a settled screen",
    ) {
        embrace.recordSpan("app-startup") {
            listOf(
                "https://config.example.com/v1/settings",
                "https://auth.example.com/v1/token",
                "https://flags.example.com/v1/evaluate",
                "https://api.example.com/v1/profile",
                "https://api.example.com/v1/feed?page=0",
                "https://api.example.com/v1/notifications/unread",
            ).forEach { url -> request(url) }

            // first screen's images, which is where most launch requests actually go
            repeat(10) { index -> request("https://cdn.example.com/thumb/$index.webp", bytesReceived = 24_000) }
            advanceTime(400)
        }

        embrace.addBreadcrumb("home shown")
        advanceTime(20_000)
    }

    val browseFeed: ScenarioSpec = ScenarioSpec(
        id = "browse_feed",
        description = "3min scrolling a feed, opening items and returning",
    ) {
        embrace.addUserSessionProperty("user.tier", "free", PropertyScope.USER_SESSION)

        repeat(6) { page ->
            embrace.recordSpan("feed-page-load", attributes = mapOf("feed.page" to page.toString())) {
                request("https://api.example.com/v1/feed?page=$page")
                repeat(4) { index -> request("https://cdn.example.com/thumb/$page-$index.webp", bytesReceived = 24_000) }
                advanceTime(150)
            }
            embrace.addBreadcrumb("scrolled to page $page")

            // user reads the page, then opens one item from it
            advanceTime(18_000)
            embrace.recordSpan("item-detail-load", attributes = mapOf("item.id" to "$page-2")) {
                request("https://api.example.com/v1/items/$page-2")
                request("https://cdn.example.com/hero/$page-2.webp", bytesReceived = 180_000)
                advanceTime(220)
            }
            embrace.addBreadcrumb("opened item $page-2")
            advanceTime(11_000)
        }
    }

    val checkoutFlow: ScenarioSpec = ScenarioSpec(
        id = "checkout_flow",
        description = "2min checkout with a failed payment and a successful retry",
    ) {
        embrace.addUserSessionProperty("user.tier", "member", PropertyScope.USER_SESSION)
        embrace.addUserSessionProperty("cart.currency", "GBP", PropertyScope.USER_SESSION)

        embrace.recordSpan("cart-load", attributes = mapOf("cart.items" to "3")) {
            request("https://api.example.com/v1/cart")
            advanceTime(240)
        }
        embrace.addBreadcrumb("cart reviewed")
        advanceTime(22_000)

        embrace.recordSpan("delivery-options-load") {
            request("https://api.example.com/v1/delivery/options")
            advanceTime(190)
        }
        embrace.addBreadcrumb("delivery selected")
        advanceTime(26_000)

        embrace.recordSpan("payment-submit") {
            request("https://pay.example.com/v1/charge", method = HttpMethod.POST, statusCode = 503)
            embrace.logWarning("payment gateway unavailable, retrying")
            advanceTime(1_400)
            request("https://pay.example.com/v1/charge", method = HttpMethod.POST)
            advanceTime(900)
        }
        embrace.addBreadcrumb("payment accepted")

        // the receipt fails to render from cache, which the app handles and logs
        embrace.logException(
            IOException("receipt cache miss"),
            severity = Severity.WARNING,
            properties = mapOf("order.id" to "A-99213"),
        )
        advanceTime(14_000)

        embrace.recordSpan("order-confirmation-load") {
            request("https://api.example.com/v1/orders/A-99213")
            advanceTime(200)
        }
        advanceTime(9_000)
    }

    val interruptedSession: ScenarioSpec = ScenarioSpec(
        id = "interrupted_session",
        description = "4min session interrupted four times by leaving and returning to the app",
    ) {
        repeat(4) { visit ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "inbox")) {
                request("https://api.example.com/v1/messages?since=$visit")
                advanceTime(170)
            }
            embrace.addBreadcrumb("inbox opened, visit $visit")
            advanceTime(24_000)

            // away replying to a message, or reading the notification that pulled them out
            backgroundAndReturn(ms = 30_000)
        }

        embrace.addBreadcrumb("returned to inbox")
        advanceTime(15_000)
    }

    val longEngagedSession: ScenarioSpec = ScenarioSpec(
        id = "long_engaged_session",
        description = "20min session across ~36 screens with a break in the middle",
    ) {
        embrace.addUserSessionProperty("user.tier", "member", PropertyScope.USER_SESSION)

        repeat(18) { step ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "category-$step")) {
                request("https://api.example.com/v1/categories/$step")
                repeat(3) { index -> request("https://cdn.example.com/thumb/$step-$index.webp", bytesReceived = 24_000) }
                advanceTime(200)
            }
            embrace.addBreadcrumb("browsed category-$step")
            advanceTime(30_000)
        }

        // the user puts the phone down for a couple of minutes, then picks it back up
        backgroundAndReturn(ms = 120_000)

        repeat(18) { step ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "saved-$step")) {
                request("https://api.example.com/v1/saved/$step")
                advanceTime(180)
            }
            embrace.addBreadcrumb("reviewed saved-$step")
            advanceTime(15_000)
        }
    }

    val all: List<ScenarioSpec> = listOf(
        quickCheck,
        launchFanout,
        browseFeed,
        checkoutFlow,
        interruptedSession,
        longEngagedSession,
    )

    fun byId(id: String): ScenarioSpec =
        all.firstOrNull { it.id == id } ?: error("No such scenario: $id")

    private fun ScenarioScope.request(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        statusCode: Int = 200,
        bytesReceived: Long = 4_096,
    ) {
        val startMs = nowMs
        advanceTime(REQUEST_DURATION_MS)
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url = url,
                httpMethod = method,
                startTime = startMs,
                endTime = nowMs,
                bytesSent = 512,
                bytesReceived = bytesReceived,
                statusCode = statusCode,
            ),
        )
    }

    private const val REQUEST_DURATION_MS = 80L
}
