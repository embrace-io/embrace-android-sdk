package io.embrace.test.fixtures

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

open class CustomWebViewClient : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }
}
