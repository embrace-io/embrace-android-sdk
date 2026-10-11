package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
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
            recordRequest("https://api.example.com/v1/notifications/4821")
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
            ).forEach { url -> recordRequest(url) }

            // first screen's images, which is where most launch requests actually go
            repeat(10) { index -> recordRequest("https://cdn.example.com/thumb/$index.webp", bytesReceived = 24_000) }
            advanceTime(400)
        }

        embrace.addBreadcrumb("home shown")
        advanceTime(10_000)
    }

    val browseFeed: ScenarioSpec = ScenarioSpec(
        id = "browse_feed",
        description = "6 feed pages, each opening one item",
    ) {
        embrace.addUserSessionProperty("user.tier", "free", PropertyScope.USER_SESSION)

        repeat(6) { page ->
            embrace.recordSpan("feed-page-load", attributes = mapOf("feed.page" to page.toString())) {
                recordRequest("https://api.example.com/v1/feed?page=$page")
                repeat(4) { index ->
                    recordRequest("https://cdn.example.com/thumb/$page-$index.webp", bytesReceived = 24_000)
                }
                advanceTime(150)
            }
            embrace.addBreadcrumb("scrolled to page $page")

            // user reads the page, then opens one item from it
            advanceTime(1_400)
            embrace.recordSpan("item-detail-load", attributes = mapOf("item.id" to "$page-2")) {
                recordRequest("https://api.example.com/v1/items/$page-2")
                recordRequest("https://cdn.example.com/hero/$page-2.webp", bytesReceived = 180_000)
                advanceTime(220)
            }
            embrace.addBreadcrumb("opened item $page-2")
            advanceTime(600)
        }
    }

    val checkoutFlow: ScenarioSpec = ScenarioSpec(
        id = "checkout_flow",
        description = "checkout with a failed payment and a successful retry",
    ) {
        embrace.addUserSessionProperty("user.tier", "member", PropertyScope.USER_SESSION)
        embrace.addUserSessionProperty("cart.currency", "GBP", PropertyScope.USER_SESSION)

        embrace.recordSpan("cart-load", attributes = mapOf("cart.items" to "3")) {
            recordRequest("https://api.example.com/v1/cart")
            advanceTime(240)
        }
        embrace.addBreadcrumb("cart reviewed")
        advanceTime(3_000)

        embrace.recordSpan("delivery-options-load") {
            recordRequest("https://api.example.com/v1/delivery/options")
            advanceTime(190)
        }
        embrace.addBreadcrumb("delivery selected")
        advanceTime(3_500)

        embrace.recordSpan("payment-submit") {
            recordRequest("https://pay.example.com/v1/charge", method = HttpMethod.POST, statusCode = 503)
            embrace.logWarning("payment gateway unavailable, retrying")
            advanceTime(1_400)
            recordRequest("https://pay.example.com/v1/charge", method = HttpMethod.POST)
            advanceTime(900)
        }
        embrace.addBreadcrumb("payment accepted")

        // the receipt fails to render from cache, which the app handles and logs
        embrace.logException(
            IOException("receipt cache miss"),
            severity = Severity.WARNING,
            properties = mapOf("order.id" to "A-99213"),
        )
        advanceTime(2_500)

        embrace.recordSpan("order-confirmation-load") {
            recordRequest("https://api.example.com/v1/orders/A-99213")
            advanceTime(200)
        }
        advanceTime(2_000)
    }

    val interruptedSession: ScenarioSpec = ScenarioSpec(
        id = "interrupted_session",
        description = "session interrupted four times by leaving and returning to the app",
    ) {
        repeat(4) { visit ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "inbox")) {
                recordRequest("https://api.example.com/v1/messages?since=$visit")
                advanceTime(170)
            }
            embrace.addBreadcrumb("inbox opened, visit $visit")
            advanceTime(2_000)

            // away replying to a message, or reading the notification that pulled them out
            backgroundAndReturn(ms = 1_500)
        }

        embrace.addBreadcrumb("returned to inbox")
        advanceTime(3_000)
    }

    val longEngagedSession: ScenarioSpec = ScenarioSpec(
        id = "long_engaged_session",
        description = "~36 screens with a break in the middle",
    ) {
        embrace.addUserSessionProperty("user.tier", "member", PropertyScope.USER_SESSION)

        repeat(18) { step ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "category-$step")) {
                recordRequest("https://api.example.com/v1/categories/$step")
                repeat(3) { index ->
                    recordRequest("https://cdn.example.com/thumb/$step-$index.webp", bytesReceived = 24_000)
                }
                advanceTime(200)
            }
            embrace.addBreadcrumb("browsed category-$step")
            advanceTime(300)
        }

        // stands in for the user putting the phone down for a couple of minutes
        backgroundAndReturn(ms = 1_500)

        repeat(18) { step ->
            embrace.recordSpan("screen-load", attributes = mapOf("screen.name" to "saved-$step")) {
                recordRequest("https://api.example.com/v1/saved/$step")
                advanceTime(180)
            }
            embrace.addBreadcrumb("reviewed saved-$step")
            advanceTime(200)
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
}
