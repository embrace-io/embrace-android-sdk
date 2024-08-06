package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.payload.WebViewInfo

internal class FakeWebViewService : FakeDataCaptureService<WebViewInfo>(), WebViewService {

    val tags = mutableListOf<String?>()

    override fun collectWebData(tag: String, message: String) {
        tags.add(tag)
    }
}
