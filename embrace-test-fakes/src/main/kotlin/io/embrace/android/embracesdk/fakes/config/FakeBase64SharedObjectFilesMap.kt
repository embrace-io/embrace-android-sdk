package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.schema.Base64SharedObjectFilesMap

class FakeBase64SharedObjectFilesMap(private val symbols: String?) : Base64SharedObjectFilesMap {
    override fun getBase64SharedObjectFilesMap(): String? {
        return symbols
    }
}
