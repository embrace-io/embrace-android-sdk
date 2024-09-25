package io.embrace.android.embracesdk.internal.storage

import java.io.InputStream

interface StorageService2 {
    fun getPayloadsByPriority(): List<PayloadReference>
    fun loadPayloadAsStream(payload: PayloadReference): InputStream?
    fun deletePayload(payload: PayloadReference)
}
