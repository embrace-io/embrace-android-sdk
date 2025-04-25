package io.embrace.android.exampleapp.ui.examples.bytecode

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

class BytecodeWebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.webViewClient = MyWebviewClient()
        setContentView(webView)
        webView.loadUrl("https://embrace.io/")
    }
}

private class MyWebviewClient : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d("BytecodeWebViewActivity", "Page started loading: $url")
    }
}
