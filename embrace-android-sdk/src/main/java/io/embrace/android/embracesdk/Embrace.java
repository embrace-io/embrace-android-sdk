package io.embrace.android.embracesdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface;
import io.embrace.android.embracesdk.internal.Systrace;
import io.embrace.android.embracesdk.internal.api.SdkApi;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.spans.EmbraceSpan;
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent;
import io.embrace.android.embracesdk.spans.ErrorCode;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import kotlin.jvm.functions.Function0;

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 * <p>
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
@SuppressWarnings("unused")
public final class Embrace implements SdkApi {

    /**
     * Singleton instance of the Embrace SDK.
     */
    @NonNull
    private static final Embrace embrace = new Embrace();

    @NonNull
    private static EmbraceImpl impl = Systrace.traceSynchronous("embrace-impl-init", EmbraceImpl::new);

    /**
     * Gets the singleton instance of the Embrace SDK.
     *
     * @return the instance of the Embrace SDK
     */
    @NonNull
    public static Embrace getInstance() {
        return embrace;
    }

    /**
     * Gets the EmbraceImpl instance. This provides access to internally visible functions
     * intended for use in the Android SDK only
     */
    @NonNull
    static EmbraceImpl getImpl() {
        return impl;
    }

    static void setImpl(@NonNull EmbraceImpl instance) {
        impl = instance;
    }

    Embrace() {
    }

    @SuppressWarnings("deprecation")
    @Override
    public void start(@NonNull Context context) {
        start(context, AppFramework.NATIVE);
    }

    /**
     * @deprecated Use {@link #start(Context)} instead.
     */
    @Override
    @Deprecated
    public void start(@NonNull Context context, @NonNull AppFramework appFramework) {
        impl.start(context, appFramework, (framework) -> null);
    }

    /**
     * @deprecated Use {@link #start(Context)} instead. The isDevMode parameter has no effect.
     */
    @Override
    @Deprecated
    public void start(@NonNull Context context, boolean isDevMode) {
        start(context);
    }

    /**
     * @deprecated Use {@link #start(Context)} instead. The isDevMode parameter has no effect.
     */
    @Override
    @Deprecated
    public void start(@NonNull Context context, boolean isDevMode, @NonNull AppFramework appFramework) {
        impl.start(context, appFramework, (framework) -> null);
    }

    @Override
    public boolean isStarted() {
        return impl.isStarted();
    }

    @Override
    public boolean setAppId(@NonNull String appId) {
        return impl.setAppId(appId);
    }

    @Override
    public void setUserIdentifier(@Nullable String userId) {
        impl.setUserIdentifier(userId);
    }

    @Override
    public void clearUserIdentifier() {
        impl.clearUserIdentifier();
    }

    @Override
    public void setUserEmail(@Nullable String email) {
        impl.setUserEmail(email);
    }

    @Override
    public void clearUserEmail() {
        impl.clearUserEmail();
    }

    @Override
    public void setUserAsPayer() {
        impl.setUserAsPayer();
    }

    @Override
    public void clearUserAsPayer() {
        impl.clearUserAsPayer();
    }

    @Override
    public void addUserPersona(@NonNull String persona) {
        impl.addUserPersona(persona);
    }

    @Override
    public void clearUserPersona(@NonNull String persona) {
        impl.clearUserPersona(persona);
    }

    @Override
    public void clearAllUserPersonas() {
        impl.clearAllUserPersonas();
    }

    @Override
    public boolean addSessionProperty(@NonNull String key, @NonNull String value, boolean permanent) {
        return impl.addSessionProperty(key, value, permanent);
    }

    @Override
    public boolean removeSessionProperty(@NonNull String key) {
        return impl.removeSessionProperty(key);
    }

    @Override
    @Nullable
    @Deprecated
    public Map<String, String> getSessionProperties() {
        return impl.getSessionProperties();
    }

    @Override
    public void setUsername(@Nullable String username) {
        impl.setUsername(username);
    }

    @Override
    public void clearUsername() {
        impl.clearUsername();
    }

    @Override
    public void startMoment(@NonNull String name) {
        startMoment(name, null);
    }

    @Override
    public void startMoment(@NonNull String name, @Nullable String identifier) {
        startMoment(name, identifier, null);
    }

    @Override
    public void startMoment(@NonNull String name,
                            @Nullable String identifier,
                            @Nullable Map<String, ?> properties) {
        impl.startMoment(name, identifier, properties);
    }

    @Override
    public void endMoment(@NonNull String name) {
        endMoment(name, null, null);
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable String identifier) {
        endMoment(name, identifier, null);
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable Map<String, ?> properties) {
        endMoment(name, null, properties);
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable String identifier, @Nullable Map<String, ?> properties) {
        impl.endMoment(name, identifier, properties);
    }

    @Override
    public void endAppStartup() {
        impl.endAppStartup();
    }

    @Override
    public void endAppStartup(@NonNull Map<String, ?> properties) {
        impl.endAppStartup(properties);
    }

    @Override
    @NonNull
    public String getTraceIdHeader() {
        return impl.getTraceIdHeader();
    }

    @Nullable
    @Override
    public String generateW3cTraceparent() {
        return impl.generateW3cTraceparent();
    }

    @Override
    public void recordNetworkRequest(@NonNull EmbraceNetworkRequest networkRequest) {
        impl.recordNetworkRequest(networkRequest);
    }

    @Override
    public void logInfo(@NonNull String message) {
        impl.logInfo(message);
    }

    @Override
    public void logWarning(@NonNull String message) {
        impl.logWarning(message);
    }

    @Override
    public void logError(@NonNull String message) {
        impl.logError(message);
    }

    @Override
    public void addBreadcrumb(@NonNull String message) {
        impl.addBreadcrumb(message);
    }

    @Override
    public void logMessage(@NonNull String message, @NonNull Severity severity) {
        impl.logMessage(message, severity);
    }

    @Override
    public void logMessage(@NonNull String message,
                           @NonNull Severity severity,
                           @Nullable Map<String, ?> properties) {
        impl.logMessage(message, severity, properties);
    }

    @Override
    public void logException(@NonNull Throwable throwable) {
        impl.logException(throwable);
    }

    @Override
    public void logException(@NonNull Throwable throwable, @NonNull Severity severity) {
        impl.logException(throwable, severity);
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, ?> properties) {
        impl.logException(throwable, severity, properties);
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, ?> properties,
                             @Nullable String message) {
        impl.logException(throwable, severity, properties, message);
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements) {
        impl.logCustomStacktrace(stacktraceElements);
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements, @NonNull Severity severity) {
        impl.logCustomStacktrace(stacktraceElements, severity);
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, ?> properties) {
        impl.logCustomStacktrace(stacktraceElements, severity, properties);
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, ?> properties,
                                    @Nullable String message) {
        impl.logCustomStacktrace(stacktraceElements, severity, properties, message);
    }

    @Override
    public synchronized void endSession() {
        endSession(false);
    }

    @Override
    public synchronized void endSession(boolean clearUserInfo) {
        impl.endSession(clearUserInfo);
    }

    @Override
    @NonNull
    public String getDeviceId() {
        return impl.getDeviceId();
    }

    @Override
    public boolean startView(@NonNull String name) {
        return impl.startView(name);
    }

    @Override
    public boolean endView(@NonNull String name) {
        return impl.endView(name);
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name) {
        return impl.createSpan(name);
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name, @Nullable EmbraceSpan parent) {
        return impl.createSpan(name, parent);
    }

    @Nullable
    @Override
    public EmbraceSpan startSpan(@NonNull String name) {
        return startSpan(name, null, null);
    }

    @Nullable
    @Override
    public EmbraceSpan startSpan(@NonNull String name, @Nullable EmbraceSpan parent) {
        return startSpan(name, parent, null);
    }

    @Nullable
    @Override
    public EmbraceSpan startSpan(@NonNull String name, @Nullable EmbraceSpan parent, @Nullable Long startTimeMs) {
        return impl.startSpan(name, parent, startTimeMs);
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @NonNull Function0<? extends T> code) {
        return recordSpan(name, null, null, null, code);
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @Nullable EmbraceSpan parent, @NonNull Function0<? extends T> code) {
        return recordSpan(name, parent, null, null, code);
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @Nullable Map<String, String> attributes,
                            @Nullable List<EmbraceSpanEvent> events, @NonNull Function0<? extends T> code) {
        return recordSpan(name, null, attributes, events, code);
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @Nullable EmbraceSpan parent, @Nullable Map<String, String> attributes,
                            @Nullable List<EmbraceSpanEvent> events, @NonNull Function0<? extends T> code) {
        return impl.recordSpan(name, parent, attributes, events, code);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs, @Nullable ErrorCode errorCode,
                                       @Nullable EmbraceSpan parent, @Nullable Map<String, String> attributes,
                                       @Nullable List<EmbraceSpanEvent> events) {
        return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs) {
        return recordCompletedSpan(name, startTimeMs, endTimeMs, null, null, null, null);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs, @Nullable ErrorCode errorCode) {
        return recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, null, null, null);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs, @Nullable EmbraceSpan parent) {
        return recordCompletedSpan(name, startTimeMs, endTimeMs, null, parent, null, null);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs, @Nullable ErrorCode errorCode,
                                       @Nullable EmbraceSpan parent) {
        return recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, null, null);
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs,
                                       @Nullable Map<String, String> attributes, @Nullable List<EmbraceSpanEvent> events) {
        return recordCompletedSpan(name, startTimeMs, endTimeMs, null, null, attributes, events);
    }

    @Nullable
    @Override
    public EmbraceSpan getSpan(@NonNull String spanId) {
        return impl.getSpan(spanId);
    }

    /**
     * Adds a [SpanExporter] to the tracer.
     *
     * @param spanExporter the span exporter to add
     */
    @Override
    public void addSpanExporter(@NonNull SpanExporter spanExporter) {
        impl.addSpanExporter(spanExporter);
    }

    @NonNull
    @Override
    public OpenTelemetry getOpenTelemetry() {
        return impl.getOpenTelemetry();
    }

    /**
     * Adds a [LogRecordExporter] to the open telemetry logger.
     *
     * @param logRecordExporter the LogRecord exporter to add
     */
    @Override
    public void addLogRecordExporter(@NonNull LogRecordExporter logRecordExporter) {
        impl.addLogRecordExporter(logRecordExporter);
    }

    @Override
    public void logPushNotification(@Nullable String title,
                                    @Nullable String body,
                                    @Nullable String topic,
                                    @Nullable String id,
                                    @Nullable Integer notificationPriority,
                                    @Nullable Integer messageDeliveredPriority,
                                    @Nullable Boolean isNotification,
                                    @Nullable Boolean hasData) {
        impl.logPushNotification(
            title,
            body,
            topic,
            id,
            notificationPriority,
            messageDeliveredPriority,
            isNotification,
            hasData
        );
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull ConsoleMessage consoleMessage) {
        if (consoleMessage.message() != null) {
            trackWebViewPerformance(tag, consoleMessage.message());
        }
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull String message) {
        impl.trackWebViewPerformance(tag, message);
    }

    @Override
    public void logWebView(@Nullable String url) {
        impl.logWebView(url);
    }

    @Nullable
    @Override
    public String getCurrentSessionId() {
        return impl.getCurrentSessionId();
    }

    @NonNull
    @Override
    public LastRunEndState getLastRunEndState() {
        return impl.getLastRunEndState();
    }

    @NonNull
    @Override
    public EmbraceInternalInterface getInternalInterface() {
        return impl.getInternalInterface();
    }

    @Nullable
    @Override
    public ReactNativeInternalInterface getReactNativeInternalInterface() {
        return impl.getReactNativeInternalInterface();
    }

    @Nullable
    @Override
    public UnityInternalInterface getUnityInternalInterface() {
        return impl.getUnityInternalInterface();
    }

    @Nullable
    @Override
    public FlutterInternalInterface getFlutterInternalInterface() {
        return impl.getFlutterInternalInterface();
    }

}
