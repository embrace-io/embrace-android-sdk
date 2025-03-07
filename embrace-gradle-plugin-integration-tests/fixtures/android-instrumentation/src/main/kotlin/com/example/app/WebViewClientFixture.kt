package com.example.app

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

class WebViewClientFixture : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }
}
