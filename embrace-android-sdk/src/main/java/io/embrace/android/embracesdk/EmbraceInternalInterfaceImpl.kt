package io.embrace.android.embracesdk

import android.util.Pair
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.payload.TapBreadcrumb

internal class EmbraceInternalInterfaceImpl(
    private val embrace: EmbraceImpl
) : EmbraceInternalInterface {

    override fun logInfo(message: String, properties: Map<String, Any>?) {
        embrace.logMessage(
            EmbraceEvent.Type.INFO_LOG,
            message,
            properties,
            null,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logWarning(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?
    ) {
        embrace.logMessage(
            EmbraceEvent.Type.WARNING_LOG,
            message,
            properties,
            null,
            stacktrace,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean,
    ) {
        embrace.logMessage(
            EmbraceEvent.Type.ERROR_LOG,
            message,
            properties,
            null,
            stacktrace,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<out StackTraceElement>?
    ) {
        embrace.logMessage(
            type.toEventType(),
            throwable.message ?: "",
            properties,
            customStackTrace ?: throwable.stackTrace,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun addBreadcrumb(message: String) {
        embrace.addBreadcrumb(message)
    }

    override fun getDeviceId(): String {
        return embrace.deviceId
    }

    override fun setUserIdentifier(userId: String?) {
        embrace.setUserIdentifier(userId)
    }

    override fun clearUserIdentifier() {
        embrace.clearUserIdentifier()
    }

    override fun setUsername(username: String?) {
        embrace.setUsername(username)
    }

    override fun clearUsername() {
        embrace.clearUsername()
    }

    override fun setUserEmail(email: String?) {
        embrace.setUserEmail(email)
    }

    override fun clearUserEmail() {
        embrace.clearUserEmail()
    }

    override fun setUserAsPayer() {
        embrace.setUserAsPayer()
    }

    override fun clearUserAsPayer() {
        embrace.clearUserAsPayer()
    }

    override fun addUserPersona(persona: String) {
        embrace.addUserPersona(persona)
    }

    override fun clearUserPersona(persona: String) {
        embrace.clearUserPersona(persona)
    }

    override fun clearAllUserPersonas() {
        embrace.clearAllUserPersonas()
    }

    override fun addSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        return embrace.addSessionProperty(key, value, permanent)
    }

    override fun removeSessionProperty(key: String): Boolean {
        return embrace.removeSessionProperty(key)
    }

    override fun getSessionProperties(): Map<String, String>? {
        return embrace.sessionProperties
    }

    override fun startMoment(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?
    ) {
        embrace.startMoment(name, identifier, properties)
    }

    override fun endMoment(name: String, identifier: String?, properties: Map<String, Any>?) {
        embrace.endMoment(name, identifier, properties)
    }

    override fun startView(name: String): Boolean {
        return embrace.startView(name)
    }

    override fun endView(name: String): Boolean {
        return embrace.endView(name)
    }

    override fun endAppStartup(properties: Map<String, Any>) {
        embrace.endAppStartup(properties)
    }

    override fun logInternalError(message: String?, details: String?) {
        embrace.logInternalError(message, details)
    }

    override fun endSession(clearUserInfo: Boolean) {
        embrace.endSession(clearUserInfo)
    }

    override fun logComposeTap(point: Pair<Float, Float>, elementName: String) {
        embrace.logTap(point, elementName, TapBreadcrumb.TapBreadcrumbType.TAP)
    }

    override fun recordCompletedNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                bytesSent,
                bytesReceived,
                statusCode,
                traceId,
                null,
                networkCaptureData
            )
        )
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        error: Throwable?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                error?.javaClass?.canonicalName ?: "",
                error?.localizedMessage ?: "",
                traceId,
                null,
                networkCaptureData
            )
        )
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                errorType ?: "",
                errorMessage ?: "",
                traceId,
                null,
                networkCaptureData
            )
        )
    }
}
