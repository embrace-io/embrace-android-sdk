package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage

internal interface DeliveryCacheManager {
    fun saveSession(sessionMessage: SessionMessage): ByteArray?
    fun loadSession(sessionId: String): SessionMessage?
    fun loadSessionBytes(sessionId: String): ByteArray?
    fun deleteSession(sessionId: String)
    fun getAllCachedSessionIds(): List<String>
    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): ByteArray?
    fun loadBackgroundActivity(backgroundActivityId: String): ByteArray?
    fun saveCrash(crash: EventMessage)
    fun loadCrash(): EventMessage?
    fun deleteCrash()
    fun savePayload(bytes: ByteArray): String
    fun loadPayload(name: String): ByteArray?
    fun deletePayload(name: String)
    fun saveFailedApiCalls(failedApiCalls: DeliveryFailedApiCalls)
    fun loadFailedApiCalls(): DeliveryFailedApiCalls
}
