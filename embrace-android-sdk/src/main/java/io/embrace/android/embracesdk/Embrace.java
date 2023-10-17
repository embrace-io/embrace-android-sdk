package io.embrace.android.embracesdk;

import static io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.logger;

import android.content.Context;
import android.util.Pair;
import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import io.embrace.android.embracesdk.config.ConfigService;
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger;
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb;
import io.embrace.android.embracesdk.spans.EmbraceSpan;
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent;
import io.embrace.android.embracesdk.spans.ErrorCode;
import kotlin.jvm.functions.Function0;

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 * <p>
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressWarnings("unused")
public final class Embrace implements EmbraceAndroidApi {

    /**
     * Singleton instance of the Embrace SDK.
     */
    private static final Embrace embrace = new Embrace();
    private static EmbraceImpl impl = new EmbraceImpl();

    @NonNull
    private final InternalEmbraceLogger internalEmbraceLogger = InternalStaticEmbraceLogger.logger;

    static final String NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE = " cannot be invoked because it contains null parameters";

    Embrace() {
    }

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

    @Override
    public void start(@NonNull Context context) {
        if (verifyNonNullParameters("start", context)) {
            start(context, true, AppFramework.NATIVE);
        }
    }

    @Override
    public void start(@NonNull Context context, boolean enableIntegrationTesting) {
        if (verifyNonNullParameters("start", context)) {
            start(context, enableIntegrationTesting, AppFramework.NATIVE);
        }
    }

    @Override
    public void start(@NonNull Context context, boolean enableIntegrationTesting, @NonNull AppFramework appFramework) {
        if (verifyNonNullParameters("start", context, appFramework)) {
            impl.start(context, enableIntegrationTesting, appFramework);
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
                            @Nullable Map<String, Object> properties) {
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
    public void endMoment(@NonNull String name, @Nullable Map<String, Object> properties) {
        if (verifyNonNullParameters("endMoment", name)) {
            endMoment(name, null, properties);
        }
    }

    @Override
    public void endMoment(@NonNull String name, @Nullable String identifier, @Nullable Map<String, Object> properties) {
        if (verifyNonNullParameters("endMoment", name)) {
            impl.endMoment(name, identifier, properties);
        }
    }

    @Override
    public void endAppStartup() {
        impl.endAppStartup(null);
    }

    @Override
    public void endAppStartup(@NonNull Map<String, Object> properties) {
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
            logMessage(message, Severity.INFO);
        }
    }

    @Override
    public void logWarning(@NonNull String message) {
        if (verifyNonNullParameters("logWarning", message)) {
            logMessage(message, Severity.WARNING);
        }
    }

    @Override
    public void logError(@NonNull String message) {
        if (verifyNonNullParameters("logError", message)) {
            logMessage(message, Severity.ERROR);
        }
    }

    /**
     * Logs a React Native Redux Action.
     */
    public void logRnAction(@NonNull String name, long startTime, long endTime,
                            @NonNull Map<String, Object> properties, int bytesSent, @NonNull String output) {
        if (verifyNonNullParameters("logRnAction", name, properties, output)) {
            impl.logRnAction(name, startTime, endTime, properties, bytesSent, output);
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
            logMessage(message, severity, null);
        }
    }

    @Override
    public void logMessage(@NonNull String message,
                           @NonNull Severity severity,
                           @Nullable Map<String, Object> properties) {
        if (verifyNonNullParameters("logMessage", message, severity)) {
            impl.logMessage(message, severity, properties);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable) {
        if (verifyNonNullParameters("logException", throwable)) {
            logException(throwable, Severity.ERROR);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable, @NonNull Severity severity) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            logException(throwable, severity, null);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, Object> properties) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            logException(throwable, severity, properties, null);
        }
    }

    @Override
    public void logException(@NonNull Throwable throwable,
                             @NonNull Severity severity,
                             @Nullable Map<String, Object> properties,
                             @Nullable String message) {
        if (verifyNonNullParameters("logException", throwable, severity)) {
            impl.logException(throwable, severity, properties, message);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements)) {
            logCustomStacktrace(stacktraceElements, Severity.ERROR);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements, @NonNull Severity severity) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            logCustomStacktrace(stacktraceElements, severity, null);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, Object> properties) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            logCustomStacktrace(stacktraceElements, severity, properties, null);
        }
    }

    @Override
    public void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                                    @NonNull Severity severity,
                                    @Nullable Map<String, Object> properties,
                                    @Nullable String message) {
        if (verifyNonNullParameters("logCustomStacktrace", (Object) stacktraceElements, severity)) {
            impl.logCustomStacktrace(stacktraceElements, severity, properties, message);
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    @InternalApi
    public void logInternalError(@Nullable String message, @Nullable String details) {
        impl.logInternalError(message, details);
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    @InternalApi
    public void logInternalError(@NonNull Throwable error) {
        impl.logInternalError(error);
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

    /**
     * Logs the fact that a particular view was entered.
     * <p>
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    @InternalApi
    public void logRnView(@NonNull String screen) {
        impl.logRnView(screen);
    }

    @Nullable
    @InternalApi
    public ConfigService getConfigService() {
        return impl.getConfigService();
    }

    @InternalApi
    void installUnityThreadSampler() {
        getImpl().installUnityThreadSampler();
    }

    @Override
    public boolean isTracingAvailable() {
        return impl.tracer.getValue().isTracingAvailable();
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name) {
        if (verifyNonNullParameters("createSpan", name)) {
            return impl.tracer.getValue().createSpan(name);
        }

        return null;
    }

    @Nullable
    @Override
    public EmbraceSpan createSpan(@NonNull String name, @Nullable EmbraceSpan parent) {
        if (verifyNonNullParameters("createSpan", name)) {
            return impl.tracer.getValue().createSpan(name, parent);
        }

        return null;
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @NonNull Function0<? extends T> code) {
        if (verifyNonNullParameters("recordSpan", name, code)) {
            return impl.tracer.getValue().recordSpan(name, code);
        }

        return code != null ? code.invoke() : null;
    }

    @Override
    public <T> T recordSpan(@NonNull String name, @Nullable EmbraceSpan parent, @NonNull Function0<? extends T> code) {
        if (verifyNonNullParameters("recordSpan", name, code)) {
            return impl.tracer.getValue().recordSpan(name, parent, code);
        }

        return code != null ? code.invoke() : null;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos, @Nullable ErrorCode errorCode,
                                       @Nullable EmbraceSpan parent, @Nullable Map<String, String> attributes,
                                       @Nullable List<EmbraceSpanEvent> events) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos, errorCode, parent, attributes, events);
        }

        return false;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos);
        }

        return false;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos, @Nullable ErrorCode errorCode) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos, errorCode);
        }

        return false;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos, @Nullable EmbraceSpan parent) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos, parent);
        }

        return false;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos, @Nullable ErrorCode errorCode,
                                       @Nullable EmbraceSpan parent) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos, errorCode, parent);
        }

        return false;
    }

    @Override
    public boolean recordCompletedSpan(@NonNull String name, long startTimeNanos, long endTimeNanos,
                                       @Nullable Map<String, String> attributes, @Nullable List<EmbraceSpanEvent> events) {
        if (verifyNonNullParameters("recordCompletedSpan", name)) {
            return impl.tracer.getValue().recordCompletedSpan(name, startTimeNanos, endTimeNanos, attributes, events);
        }

        return false;
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
     * Gets the {@link ReactNativeInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for React Native.
     */
    @Nullable
    @InternalApi
    public ReactNativeInternalInterface getReactNativeInternalInterface() {
        return impl.getReactNativeInternalInterface();
    }

    /**
     * Gets the {@link UnityInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Unity.
     */
    @Nullable
    @InternalApi
    public UnityInternalInterface getUnityInternalInterface() {
        return impl.getUnityInternalInterface();
    }

    /**
     * Gets the {@link FlutterInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Flutter.
     */
    @Nullable
    @InternalApi
    public FlutterInternalInterface getFlutterInternalInterface() {
        return impl.getFlutterInternalInterface();
    }

    /**
     * Sets the Embrace Flutter SDK version - this is not intended for public use.
     */
    @InternalApi
    public void setEmbraceFlutterSdkVersion(@Nullable String version) {
        impl.setEmbraceFlutterSdkVersion(version);
    }

    /**
     * Sets the Dart version - this is not intended for public use.
     */
    @InternalApi
    public void setDartVersion(@Nullable String version) {
        impl.setDartVersion(version);
    }

    /**
     * Logs a handled Dart error to the Embrace SDK - this is not intended for public use.
     */
    @InternalApi
    public void logHandledDartException(
        @Nullable String stack,
        @Nullable String name,
        @Nullable String message,
        @Nullable String context,
        @Nullable String library
    ) {
        impl.logDartException(stack, name, message, context, library, LogExceptionType.HANDLED);
    }

    /**
     * Logs an unhandled Dart error to the Embrace SDK - this is not intended for public use.
     */
    @InternalApi
    public void logUnhandledDartException(
        @Nullable String stack,
        @Nullable String name,
        @Nullable String message,
        @Nullable String context,
        @Nullable String library
    ) {
        impl.logDartException(stack, name, message, context, library, LogExceptionType.UNHANDLED);
    }

    @InternalApi
    public void sampleCurrentThreadDuringAnrs() {
        impl.sampleCurrentThreadDuringAnrs();
    }

    /**
     * Logs taps from Compose views
     * @param point                    Position of the captured clicked
     * @param elementName              Name of the clicked element
     */
    @InternalApi
    public void logComposeTap(@NonNull Pair<Float, Float> point, @NonNull String elementName) {
        impl.getEmbraceInternalInterface().logComposeTap(point, elementName);
    }

    /**
     * Allows Unity customers to verify their integration.
     */
    void verifyUnityIntegration() {
        EmbraceSamples.verifyIntegration();
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
                PushNotificationBreadcrumb.NotificationType.Builder.notificationTypeFor(hasData, isNotification)
            );
        }
    }

    /**
     * Determine if a network call should be captured based on the network capture rules
     *
     * @param url    the url of the network call
     * @param method the method of the network call
     * @return the network capture rule to apply or null
     */
    @InternalApi
    public boolean shouldCaptureNetworkBody(@NonNull String url, @NonNull String method) {
        if (isStarted()) {
            return impl.shouldCaptureNetworkCall(url, method);
        } else {
            internalEmbraceLogger.logSDKNotInitialized("Embrace SDK is not initialized yet, cannot check for capture rules.");
            return false;
        }
    }

    @InternalApi
    public void setProcessStartedByNotification() {
        impl.setProcessStartedByNotification();
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull ConsoleMessage consoleMessage) {
        if (verifyNonNullParameters("trackWebViewPerformance", tag, consoleMessage)) {
            if (consoleMessage.message() != null) {
                trackWebViewPerformance(tag, consoleMessage.message());
            } else {
                logger.logDebug("Empty WebView console message.");
            }
        }
    }

    @Override
    public void trackWebViewPerformance(@NonNull String tag, @NonNull String message) {
        if (verifyNonNullParameters("trackWebViewPerformance", tag, message)) {
            impl.trackWebViewPerformance(tag, message);
        }
    }

    /**
     * Get the ID for the current session.
     * Returns null if a session has not been started yet or the SDK hasn't been initialized.
     *
     * @return The ID for the current Session, if available.
     */
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

    private boolean verifyNonNullParameters(@NonNull String functionName, @NonNull Object... params) {
        for (Object param : params) {
            if (param == null) {
                final String errorMessage = functionName + NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE;
                if (isStarted()) {
                    internalEmbraceLogger.logError(errorMessage, new IllegalArgumentException(errorMessage), true);
                } else {
                    internalEmbraceLogger.logSDKNotInitialized(errorMessage);
                }
                return false;
            }
        }
        return true;
    }
}
