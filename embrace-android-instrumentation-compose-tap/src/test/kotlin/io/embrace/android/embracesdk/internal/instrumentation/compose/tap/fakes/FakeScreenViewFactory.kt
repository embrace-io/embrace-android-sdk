package io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes

import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView

/**
 * Builds a stock 1080×2400 phone-screen view hierarchy for use in unit tests:
 *
 * - DecorView (1080×2400)
 *   - StatusBar (1080×88, at y=0)
 *   - ContentArea (1080×2192, at y=88)
 *     - Toolbar (1080×200, at y=88)
 *     - Body (1080×1992, at y=288) — optional [ComposeView] is attached here
 *   - NavBar (1080×120, at y=2280)
 */
internal object FakeScreenViewFactory {

    private const val SCREEN_WIDTH = 1080
    private const val SCREEN_HEIGHT = 2400
    private const val STATUS_BAR_HEIGHT = 88
    private const val TOOLBAR_HEIGHT = 200
    private const val NAV_BAR_HEIGHT = 120

    fun createScreen(context: Context, composeView: ComposeView? = null): View {
        val contentTop = STATUS_BAR_HEIGHT
        val contentBottom = SCREEN_HEIGHT - NAV_BAR_HEIGHT
        val contentHeight = contentBottom - contentTop
        val bodyTop = contentTop + TOOLBAR_HEIGHT
        val bodyHeight = contentHeight - TOOLBAR_HEIGHT

        val statusBar = PositionedView(
            context = context,
            winX = 0,
            winY = 0,
            width = SCREEN_WIDTH,
            height = STATUS_BAR_HEIGHT,
        )
        val toolbar = PositionedView(
            context = context,
            winX = 0,
            winY = contentTop,
            width = SCREEN_WIDTH,
            height = TOOLBAR_HEIGHT,
        )
        val body = PositionedFrameLayout(
            context = context,
            winX = 0,
            winY = bodyTop,
            width = SCREEN_WIDTH,
            height = bodyHeight,
        )
        val navBar = PositionedView(
            context = context,
            winX = 0,
            winY = contentBottom,
            width = SCREEN_WIDTH,
            height = NAV_BAR_HEIGHT,
        )

        if (composeView != null) {
            body.addView(composeView)
        }

        val content = PositionedFrameLayout(
            context = context,
            winX = 0,
            winY = contentTop,
            width = SCREEN_WIDTH,
            height = contentHeight,
        )
        content.addView(toolbar)
        content.addView(body)

        val decor = PositionedFrameLayout(
            context = context,
            winX = 0,
            winY = 0,
            width = SCREEN_WIDTH,
            height = SCREEN_HEIGHT,
        )
        decor.addView(statusBar)
        decor.addView(content)
        decor.addView(navBar)

        return decor
    }
}
