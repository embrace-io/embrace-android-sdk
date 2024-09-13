package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.payload.WebViewInfo

class FakeWebViewService : FakeDataCaptureService<WebViewInfo>(), WebViewService {

    val tags: MutableList<String?> = mutableListOf()

    override fun collectWebData(tag: String, message: String) {
        tags.add(tag)
    }
}
