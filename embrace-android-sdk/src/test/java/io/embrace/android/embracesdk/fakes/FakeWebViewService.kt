package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.payload.WebViewInfo

internal class FakeWebViewService : FakeDataCaptureService<WebViewInfo>(), WebViewService {

    override fun collectWebData(tag: String, message: String) {
    }
}
