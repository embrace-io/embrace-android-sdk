package io.embrace.android.embracesdk;

import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.embrace.android.embracesdk.spans.TracingApi;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Declares the functions that consist of Embrace's public API. You should not use
 * {@link EmbraceApi} directly or implement it in your own custom classes,
 * as new functions may be added in future. Use the {@link Embrace} class instead.
 */
interface EmbraceApi extends LogsApi, MomentsApi, NetworkRequestApi, SessionApi, UserApi, TracingApi {
    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    boolean setAppId(@NonNull String appId);

    /**
     * Adds a breadcrumb.
     * <p>
     * Breadcrumbs track a user's journey through the application and will be shown on the timeline.
     *
     * @param message the name of the breadcrumb to log
     */
    void addBreadcrumb(@NonNull String message);

    /**
     * Retrieve the HTTP request header to extract trace ID from.
     *
     * @return the Trace ID header.
     */
    @NonNull
    String getTraceIdHeader();

    /**
     * Randomly generate a W3C-compliant traceparent
     */
    @NonNull
    String generateW3cTraceparent();

    /**
     * Get the user identifier assigned to the device by Embrace
     *
     * @return the device identifier created by Embrace
     */
    @NonNull
    String getDeviceId();

    /**
     * Listen to performance-tracking JavaScript previously embedded in the website's code.
     * The WebView being tracked must have JavaScript enabled.
     *
     * @param tag            a name used to identify the WebView being tracked
     * @param consoleMessage the console message collected from the WebView
     */
    void trackWebViewPerformance(@NonNull String tag, @NonNull ConsoleMessage consoleMessage);

    /**
     * Listen to performance-tracking JavaScript previously embedded in the website's code.
     * The WebView being tracked must have JavaScript enabled.
     *
     * @param tag     a name used to identify the WebView being tracked
     * @param message the console message collected from the WebView
     */
    void trackWebViewPerformance(@NonNull String tag, @NonNull String message);

    /**
     * Get the ID for the current session.
     * Returns null if a session has not been started yet or the SDK hasn't been initialized.
     *
     * @return The ID for the current Session, if available.
     */
    @Nullable
    String getCurrentSessionId();

    /**
     * Get the end state of the last run of the application.
     *
     * @return LastRunEndState enum value representing the end state of the last run.
     */
    @NonNull
    Embrace.LastRunEndState getLastRunEndState();

    /**
     * Adds a [SpanExporter] to the tracer.
     *
     * @param spanExporter the span exporter to add
     */
    void addSpanExporter(@NonNull SpanExporter spanExporter);

    /**
     * Adds a [LogRecordExporter] to the open telemetry logger.
     *
     * @param logRecordExporter the LogRecord exporter to add
     */
    void addLogRecordExporter(@NonNull LogRecordExporter logRecordExporter);
}
