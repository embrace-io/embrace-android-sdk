package io.embrace.android.embracesdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface;
import io.embrace.android.embracesdk.internal.Systrace;
import io.embrace.android.embracesdk.internal.api.EmbraceAndroidApi;
import io.embrace.android.embracesdk.internal.api.EmbraceApi;
import io.embrace.android.embracesdk.internal.api.LogsApi;
import io.embrace.android.embracesdk.internal.api.MomentsApi;
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi;
import io.embrace.android.embracesdk.internal.api.OTelApi;
import io.embrace.android.embracesdk.internal.api.SdkStateApi;
import io.embrace.android.embracesdk.internal.api.SessionApi;
import io.embrace.android.embracesdk.internal.api.UserApi;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.spans.EmbraceSpan;
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent;
import io.embrace.android.embracesdk.spans.ErrorCode;
import io.embrace.android.embracesdk.spans.TracingApi;
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
public final class Embrace implements
    LogsApi,
    MomentsApi,
    NetworkRequestApi,
    SessionApi,
    UserApi,
    TracingApi,
    EmbraceApi,
    EmbraceAndroidApi,
    SdkStateApi,
    OTelApi {

    /**
     * Singleton instance of the Embrace SDK.
     */
    @NonNull
    private static final Embrace embrace = new Embrace();

    private static EmbraceImpl impl = Systrace.traceSynchronous("embrace-impl-init", EmbraceImpl::new);

    static final String NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE = " cannot be invoked because it contains null parameters";

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

    static void setImpl(@Nullable EmbraceImpl instance) {
        impl = instance;
    }

    Embrace() {
    }

    @Override
    public void start(@NonNull Context context) {
        if (verifyNonNullParameters("start", context)) {
            start(context, AppFramework.NATIVE);
        }
    }

    @Override
    public void start(@NonNull Context context, @NonNull AppFramework appFramework) {
        if (verifyNonNullParameters("start", context, appFramework)) {
            impl.start(context, appFramework, () -> null);
        }
    }

    /**
     * @deprecated Use {@link #start(Context)} instead. The isDevMode parameter has no effect.
     */
    @Override
    @Deprecated
    public void start(@NonNull Context context, boolean isDevMode) {
        if (verifyNonNullParameters("start", context)) {
            start(context);
        }
    }

    /**
     * @deprecated Use {@link #start(Context, AppFramework)} instead. The isDevMode parameter has no effect.
     */
    @Override
    @Deprecated
    public void start(@NonNull Context context, boolean isDevMode, @NonNull AppFramework appFramework) {
        if (verifyNonNullParameters("start", context, appFramework)) {
            impl.start(context, appFramework, () -> null);
        }
    }

    @Override
    public boolean isStarted() {
        return impl.isStarted();
    }

    @Override
    public boolean setAppId(@NonNull String appId) {
        if (verifyNonNullParameters("setAppId", appId)) {
            return impl.setAppId(appId);
        } else {
            return false;
        }
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
        if (verifyNonNullParameters("addUserPersona", persona)) {
            impl.addUserPersona(persona);
        }
    }

    @Override
    public void clearUserPersona(@NonNull String persona) {
        if (verifyNonNullParameters("clearUserPersona", persona)) {
            impl.clearUserPersona(persona);
        }
    }

    @Override
    public void clearAllUserPersonas() {
        impl.clearAllUserPersonas();
    }

    @Override
    public boolean addSessionProperty(@NonNull String key, @NonNull String value, boolean permanent) {
        if (verifyNonNullParameters("addSessionProperty", key, value)) {
            return impl.addSessionProperty(key, value, permanent);
        }

        return false;
    }

    @Override
    public boolean removeSessionProperty(@NonNull String key) {
        if (verifyNonNullParameters("removeSessionProperty", key)) {
            return impl.removeSessionProperty(key);
        }

        return false;
    }

    @Override
    @Nullable
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
        if (verifyNonNullParameters("startMoment", name)) {
            startMoment(name, null);
        }
    }

    @Override
    public void startMoment(@NonNull String name, @Nullable String identifier) {
        if (verifyNonNullParameters("startMoment", name)) {
            startMoment(name, identifier, null);
        }
    }

    @Override
    public void startMoment(@NonNull String name,
                            @Nullable String identifier,
                            @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("startMoment", name)) {
            impl.startMoment(name, identifier, properties);
        }
    }

    @Override
    public void endMoment(@NonNull String name) {
        if (verifyNonNullParameters("endMoment", name)) {
            endMoment(name, null, null);
        }
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable String identifier) {
        if (verifyNonNullParameters("endMoment", name)) {
            endMoment(name, identifier, null);
        }
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("endMoment", name)) {
            endMoment(name, null, properties);
        }
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable String identifier, @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("endMoment", name)) {
            impl.endMoment(name, identifier, properties);
        }
    }

    @Override
    public void endAppStartup() {
        impl.endAppStartup();
    }

    @Override
    public void endAppStartup(@NonNull Map<String, ?> properties) {
        if (verifyNonNullParameters("endAppStartup", properties)) {
            impl.endAppStartup(properties);
        }
    }

    @Override
    @NonNull
    public String getTraceIdHeader() {
        return impl.getTraceIdHeader();
    }

    @NonNull
    @Override
    public String generateW3cTraceparent() {
        return impl.generateW3cTraceparent();
    }

    @Override
    public void recordNetworkRequest(@NonNull EmbraceNetworkRequest networkRequest) {
        if (verifyNonNullParameters("recordNetworkRequest", networkRequest)) {
            impl.recordNetworkRequest(networkRequest);
        }
    }

    @Override
    public void logInfo(@NonNull String message) {
        if (verifyNonNullParameters("logInfo", message)) {
            impl.logInfo(message);
        }
    }

    @Override
    public void logWarning(@NonNull String message) {
        if (verifyNonNullParameters("logWarning", message)) {
            impl.logWarning(message);
        }
    }

    @Override
    public void logError(@NonNull String message) {
        if (verifyNonNullParameters("logError", message)) {
            impl.logError(message);
        }
    }

    @Override
    public void addBreadcrumb(@NonNull String message) {
        if (verifyNonNullParameters("addBreadcrumb", message)) {
            impl.addBreadcrumb(message);
        }
    }

    @Override
    public void logMessage(@NonNull String message, @NonNull Severity severity) {
        if (verifyNonNullParameters("logMessage", message, severity)) {
            impl.logMessage(message, severity);
        }
    }

    @Override
    public void logMessage(@NonNull String message,
                           @NonNull Severity severity,
                           @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("logMessage", message, severity)) {
            impl.logMessage(message, severity, properties);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable) {
        if (verifyNonNullParameters("logException", throwable)) {
            impl.logException(throwable);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable, @NonNull Severity severity) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            impl.logException(throwable, severity);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            impl.logException(throwable, severity, properties);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, ?> properties,
                             @Nullable String message) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            impl.logException(throwable, severity, properties, message);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements)) {
            impl.logCustomStacktrace(stacktraceElements);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements, @NonNull Severity severity) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            impl.logCustomStacktrace(stacktraceElements, severity);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, ?> properties) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            impl.logCustomStacktrace(stacktraceElements, severity, properties);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, ?> properties,
                                    @Nullable String message) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            impl.logCustomStacktrace(stacktraceElements, severity, properties, message);
        }
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
        if (verifyNonNullParameters("startView", name)) {
            return impl.startView(name);
        }
        return false;
    }

    @Override
    public boolean endView(@NonNull String name) {
        if (verifyNonNullParameters("endView", name)) {
            return impl.endView(name);
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isTracingAvailable() {
        return impl.isTracingAvailable();
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name) {
        if (verifyNonNullParameters("createSpan", name)) {
            return impl.createSpan(name);
        }

        return null;
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name, @Nullable EmbraceSpan parent) {
        if (verifyNonNullParameters("createSpan", name)) {
            return impl.createSpan(name, parent);
        }

        return null;
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
        if (verifyNonNullParameters("startSpan", name)) {
            return impl.startSpan(name, parent, startTimeMs);
        }

        return null;
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
        if (verifyNonNullParameters("recordSpan", name, code)) {
            return impl.recordSpan(name, parent, attributes, events, code);
        }

        return code != null ? code.invoke() : null;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeMs, long endTimeMs, @Nullable ErrorCode errorCode,
                                       @Nullable EmbraceSpan parent, @Nullable Map<String, String> attributes,
                                       @Nullable List<EmbraceSpanEvent> events) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events);
        }

        return false;
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
        if (verifyNonNullParameters("getSpan", spanId)) {
            return impl.getSpan(spanId);
        }

        return null;
    }

    /**
     * Adds a [SpanExporter] to the tracer.
     *
     * @param spanExporter the span exporter to add
     */
    @Override
    public void addSpanExporter(@NonNull SpanExporter spanExporter) {
        if (verifyNonNullParameters("addSpanExporter", spanExporter)) {
            impl.addSpanExporter(spanExporter);
        }
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
        if (verifyNonNullParameters("addLogRecordExporter", logRecordExporter)) {
            impl.addLogRecordExporter(logRecordExporter);
        }
    }

    @Override
    public void logPushNotification(@Nullable String title,
                                    @Nullable String body,
                                    @Nullable String topic,
                                    @Nullable String id,
                                    @Nullable Integer notificationPriority,
                                    @NonNull Integer messageDeliveredPriority,
                                    @NonNull Boolean isNotification,
                                    @NonNull Boolean hasData) {
        if (verifyNonNullParameters("logPushNotification", messageDeliveredPriority, isNotification, hasData)) {
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
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull ConsoleMessage consoleMessage) {
        if (verifyNonNullParameters("trackWebViewPerformance", tag, consoleMessage)) {
            if (consoleMessage.message() != null) {
                trackWebViewPerformance(tag, consoleMessage.message());
            }
        }
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull String message) {
        if (verifyNonNullParameters("trackWebViewPerformance", tag, message)) {
            impl.trackWebViewPerformance(tag, message);
        }
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

    /**
     * Get internal interface for the intra-Embrace, not-publicly-supported API
     *
     * @hide
     */
    @NonNull
    @InternalApi
    public EmbraceInternalInterface getInternalInterface() {
        return impl.getEmbraceInternalInterface();
    }

    /**
     * Gets the {@link ReactNativeInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for React Native. Not part of the supported public API.
     *
     * @hide
     */
    @Nullable
    @InternalApi
    public ReactNativeInternalInterface getReactNativeInternalInterface() {
        return impl.getReactNativeInternalInterface();
    }

    /**
     * @hide Gets the {@link UnityInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Unity. Not part of the supported public API.
     * @hide
     */
    @Nullable
    @InternalApi
    public UnityInternalInterface getUnityInternalInterface() {
        return impl.getUnityInternalInterface();
    }

    /**
     * Gets the {@link FlutterInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Flutter. Not part of the supported public API.
     *
     * @hide
     */
    @Nullable
    @InternalApi
    public FlutterInternalInterface getFlutterInternalInterface() {
        return impl.getFlutterInternalInterface();
    }

    private boolean verifyNonNullParameters(@NonNull String functionName, @NonNull Object... params) {
        for (Object param : params) {
            if (param == null) {
                final String errorMessage = functionName + NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE;
                if (isStarted()) {
                    impl.getEmbraceInternalInterface().logInternalError(new IllegalArgumentException(errorMessage));
                }
                return false;
            }
        }
        return true;
    }

    /**
     * The AppFramework that is in use.
     */
    public enum AppFramework {
        NATIVE(1),
        REACT_NATIVE(2),
        UNITY(3),
        FLUTTER(4);

        private final int value;

        AppFramework(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Enum representing the end state of the last run of the application.
     */
    public enum LastRunEndState {
        /**
         * The SDK has not been started yet.
         */
        INVALID(0),

        /**
         * The last run resulted in a crash.
         */
        CRASH(1),

        /**
         * The last run did not result in a crash.
         */
        CLEAN_EXIT(2);

        private final int value;

        LastRunEndState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
