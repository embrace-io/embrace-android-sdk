package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.payload.WebViewInfo

public class FakeWebViewService : FakeDataCaptureService<WebViewInfo>(), WebViewService {

    public val tags: MutableList<String?> = mutableListOf()

    override fun collectWebData(tag: String, message: String) {
        tags.add(tag)
    }
}
