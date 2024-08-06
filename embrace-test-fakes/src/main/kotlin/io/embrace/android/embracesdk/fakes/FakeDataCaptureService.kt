package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.DataCaptureService

internal abstract class FakeDataCaptureService<T> : DataCaptureService<List<T>?> {

    internal var data: List<T>? = mutableListOf()

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<T>? = data
}
