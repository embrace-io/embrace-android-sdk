package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.storage.PayloadReference
import io.embrace.android.embracesdk.internal.storage.StorageService2
import java.io.InputStream

class FakeStorageService2(
    payloads: List<PayloadReference> = emptyList()
) : StorageService2 {
    val cachedPayloads = LinkedHashSet<PayloadReference>().apply {
        addAll(payloads)
    }

    override fun getPayloadsByPriority(): List<PayloadReference> = cachedPayloads.toList()

    override fun loadPayloadAsStream(payload: PayloadReference): InputStream? {
        return null
    }

    override fun deletePayload(payload: PayloadReference) {
        cachedPayloads.remove(payload)
    }
}
