@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public class Embrace private constructor(
    internal var impl: EmbraceImpl = Systrace.traceSynchronous("embrace-impl-init") { EmbraceImpl() },
) : SdkApi {

    public companion object {

        /**
         * Singleton instance of the Embrace SDK.
         */
        private val instance: Embrace = Embrace()

        /**
         * Gets the singleton instance of the Embrace SDK.
         *
         * @return the instance of the Embrace SDK
         */
        @JvmStatic
        public fun getInstance(): Embrace = instance
    }

    override fun start(context: Context) {
        impl.start(context)
    }

    @Deprecated("Use {@link #start(Context)} instead.")
    override fun start(context: Context, appFramework: AppFramework) {
        impl.start(context, appFramework)
    }

    override val isStarted: Boolean
        get() = impl.isStarted

    override fun setAppId(appId: String): Boolean {
        return impl.setAppId(appId)
    }

    override fun setUserIdentifier(userId: String?) {
        impl.setUserIdentifier(userId)
    }

    override fun clearUserIdentifier() {
        impl.clearUserIdentifier()
    }

    override fun setUserEmail(email: String?) {
        impl.setUserEmail(email)
    }

    override fun clearUserEmail() {
        impl.clearUserEmail()
    }

    override fun setUserAsPayer() {
        impl.setUserAsPayer()
    }

    override fun clearUserAsPayer() {
        impl.clearUserAsPayer()
    }

    override fun addUserPersona(persona: String) {
        impl.addUserPersona(persona)
    }

    override fun clearUserPersona(persona: String) {
        impl.clearUserPersona(persona)
    }

    override fun clearAllUserPersonas() {
        impl.clearAllUserPersonas()
    }

    override fun addSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        return impl.addSessionProperty(key, value, permanent)
    }

    override fun removeSessionProperty(key: String): Boolean {
        return impl.removeSessionProperty(key)
    }

    override fun setUsername(username: String?) {
        impl.setUsername(username)
    }

    override fun clearUsername() {
        impl.clearUsername()
    }

    override val traceIdHeader: String
        get() = impl.traceIdHeader

    override fun generateW3cTraceparent(): String? {
        return impl.generateW3cTraceparent()
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        impl.recordNetworkRequest(networkRequest)
    }

    override fun logInfo(message: String) {
        impl.logInfo(message)
    }

    override fun logWarning(message: String) {
        impl.logWarning(message)
    }

    override fun logError(message: String) {
        impl.logError(message)
    }

    override fun addBreadcrumb(message: String) {
        impl.addBreadcrumb(message)
    }

    override fun logMessage(message: String, severity: Severity) {
        impl.logMessage(message, severity)
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        impl.logMessage(message, severity, properties)
    }

    override fun logException(throwable: Throwable) {
        impl.logException(throwable)
    }

    override fun logException(throwable: Throwable, severity: Severity) {
        impl.logException(throwable, severity)
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        impl.logException(throwable, severity, properties)
    }

    override fun logException(
        throwable: Throwable,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    ) {
        impl.logException(throwable, severity, properties, message)
    }

    override fun logCustomStacktrace(stacktraceElements: Array<StackTraceElement>) {
        impl.logCustomStacktrace(stacktraceElements)
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
    ) {
        impl.logCustomStacktrace(stacktraceElements, severity)
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
    ) {
        impl.logCustomStacktrace(stacktraceElements, severity, properties)
    }

    override fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?,
    ) {
        impl.logCustomStacktrace(stacktraceElements, severity, properties, message)
    }

    override fun endSession() {
        impl.endSession()
    }

    override fun endSession(clearUserInfo: Boolean) {
        impl.endSession(clearUserInfo)
    }

    override val deviceId: String
        get() = impl.deviceId

    override fun startView(name: String): Boolean {
        return impl.startView(name)
    }

    override fun endView(name: String): Boolean {
        return impl.endView(name)
    }

    override fun createSpan(name: String): EmbraceSpan? {
        return impl.createSpan(name)
    }

    override fun createSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? {
        return impl.createSpan(name, parent)
    }

    override fun startSpan(name: String): EmbraceSpan? {
        return impl.startSpan(name)
    }

    override fun startSpan(name: String, parent: EmbraceSpan?): EmbraceSpan? {
        return impl.startSpan(name, parent)
    }

    override fun startSpan(name: String, parent: EmbraceSpan?, startTimeMs: Long?): EmbraceSpan? {
        return impl.startSpan(name, parent, startTimeMs)
    }

    override fun <T> recordSpan(name: String, code: Function0<T>): T {
        return impl.recordSpan(name, code)
    }

    override fun <T> recordSpan(name: String, parent: EmbraceSpan?, code: Function0<T>): T {
        return impl.recordSpan(name, parent, code)
    }

    override fun <T> recordSpan(
        name: String,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: Function0<T>,
    ): T {
        return impl.recordSpan(name, attributes, events, code)
    }

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        code: Function0<T>,
    ): T {
        return impl.recordSpan(name, parent, attributes, events, code)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
    ): Boolean {
        return impl.recordCompletedSpan(
            name,
            startTimeMs,
            endTimeMs,
            errorCode,
            parent,
            attributes,
            events
        )
    }

    override fun recordCompletedSpan(name: String, startTimeMs: Long, endTimeMs: Long): Boolean {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
    ): Boolean {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
    ): Boolean {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, parent)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
    ): Boolean {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent)
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
    ): Boolean {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, attributes, events)
    }

    override fun getSpan(spanId: String): EmbraceSpan? {
        return impl.getSpan(spanId)
    }

    /**
     * Adds a [SpanExporter] to the tracer.
     *
     * @param spanExporter the span exporter to add
     */
    override fun addSpanExporter(spanExporter: SpanExporter) {
        impl.addSpanExporter(spanExporter)
    }

    override fun getOpenTelemetry(): OpenTelemetry {
        return impl.getOpenTelemetry()
    }

    /**
     * Adds a [LogRecordExporter] to the open telemetry logger.
     *
     * @param logRecordExporter the LogRecord exporter to add
     */
    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        impl.addLogRecordExporter(logRecordExporter)
    }

    override fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int?,
        isNotification: Boolean?,
        hasData: Boolean?,
    ) {
        impl.logPushNotification(
            title,
            body,
            topic,
            id,
            notificationPriority,
            messageDeliveredPriority,
            isNotification,
            hasData
        )
    }

    override fun trackWebViewPerformance(tag: String, consoleMessage: ConsoleMessage) {
        impl.trackWebViewPerformance(tag, consoleMessage)
    }

    override fun trackWebViewPerformance(tag: String, message: String) {
        impl.trackWebViewPerformance(tag, message)
    }

    override fun logWebView(url: String?) {
        impl.logWebView(url)
    }

    override val currentSessionId: String?
        get() = impl.currentSessionId

    override val lastRunEndState: LastRunEndState
        get() = impl.lastRunEndState
}
