package io.embrace.android.embracesdk.instrumentation.leaks

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class WebViewLeakScannerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val logger = FakeInternalLogger()

    @Test
    fun `finds a webview nested inside a view group`() {
        val webView = WebView(context)
        val root = FrameLayout(context).apply { addView(FrameLayout(context).apply { addView(webView) }) }
        val found = mutableListOf<WebView>()

        WebViewLeakScanner.scan(root, logger) { found.add(it) }

        assertEquals(listOf(webView), found)
    }

    @Test
    fun `finds nothing in a tree with no webview`() {
        val root = FrameLayout(context).apply { addView(View(context)) }
        val found = mutableListOf<WebView>()

        WebViewLeakScanner.scan(root, logger) { found.add(it) }

        assertTrue(found.isEmpty())
    }

    @Test
    fun `is a no-op for a null root`() {
        val found = mutableListOf<WebView>()

        WebViewLeakScanner.scan(null, logger) { found.add(it) }

        assertTrue(found.isEmpty())
    }

    @Test
    fun `does not descend past the max depth`() {
        val webView = WebView(context)
        var innermost: View = FrameLayout(context).apply { addView(webView) }
        // 40 more wrapping layers puts the webview's direct parent at depth 40, one past the cap.
        repeat(40) { innermost = FrameLayout(context).apply { addView(innermost) } }
        val found = mutableListOf<WebView>()

        WebViewLeakScanner.scan(innermost, logger) { found.add(it) }

        assertTrue("a webview nested deeper than the depth cap should not be found", found.isEmpty())
    }

    @Test
    fun `an exception during traversal is caught and logged, not thrown`() {
        val root = ThrowingViewGroup(context)
        val found = mutableListOf<WebView>()

        WebViewLeakScanner.scan(root, logger) { found.add(it) }

        assertTrue(found.isEmpty())
        assertEquals(1, logger.errorMessages.size)
    }
}

/** A [FrameLayout] that throws when the traversal asks how many children it has. */
internal class ThrowingViewGroup(context: Context) : FrameLayout(context) {
    override fun getChildCount(): Int = error("boom")
}
