package io.embrace.test.fixtures

import android.graphics.Bitmap
import android.webkit.WebView

class ExtendedCustomWebViewClient : CustomWebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, "http://google.com", favicon)
    }
}
