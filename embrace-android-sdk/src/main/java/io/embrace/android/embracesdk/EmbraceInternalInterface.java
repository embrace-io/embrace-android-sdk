package io.embrace.android.embracesdk;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.http.NetworkCaptureData;

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their sole source of communication
 * with the Android SDK. This is not publicly supported and methods can change at any time.
 */
@InternalApi
public interface EmbraceInternalInterface {

    /**
     * {@see Embrace#logInfo}
     */
    void logInfo(@NonNull String message,
                 @Nullable Map<String, Object> properties);

    /**
     * {@see Embrace#logWarning}
     */
    void logWarning(@NonNull String message,
                    @Nullable Map<String, Object> properties,
                    @Nullable String stacktrace);

    /**
     * {@see Embrace#logError}
     */
    void logError(@NonNull String message,
                  @Nullable Map<String, Object> properties,
                  @Nullable String stacktrace,
                  boolean isException);

    /**
     * {@see Embrace#logHandledException}
     */
    void logHandledException(@NonNull Throwable throwable,
                             @NonNull LogType type,
                             @Nullable Map<String, Object> properties,
                             @Nullable StackTraceElement[] customStackTrace);

    /**
     * See {@link Embrace#recordNetworkRequest(EmbraceNetworkRequest)}
     */
    void recordCompletedNetworkRequest(@NonNull String url,
                                       @NonNull String httpMethod,
                                       long startTime,
                                       long endTime,
                                       long bytesSent,
                                       long bytesReceived,
                                       int statusCode,
                                       @Nullable String traceId,
                                       @Nullable NetworkCaptureData networkCaptureData);

    /**
     * See {@link Embrace#recordNetworkRequest(EmbraceNetworkRequest)}
     */
    void recordIncompleteNetworkRequest(@NonNull String url,
                                        @NonNull String httpMethod,
                                        long startTime,
                                        long endTime,
                                        @Nullable Throwable error,
                                        @Nullable String traceId,
                                        @Nullable NetworkCaptureData networkCaptureData);

    /**
     * See {@link Embrace#recordNetworkRequest(EmbraceNetworkRequest)}
     */
    void recordIncompleteNetworkRequest(@NonNull String url,
                                        @NonNull String httpMethod,
                                        long startTime,
                                        long endTime,
                                        @Nullable String errorType,
                                        @Nullable String errorMessage,
                                        @Nullable String traceId,
                                        @Nullable NetworkCaptureData networkCaptureData);

    /**
     * Record a network request and overwrite any previously recorded request with the same callId
     *
     * @param callId                the ID with which the request will be identified internally. The session will only contain one recorded
     *                              request with a given ID - last writer wins.
     * @param embraceNetworkRequest the request to be recorded
     */
    void recordAndDeduplicateNetworkRequest(@NonNull String callId,
                                            @NonNull EmbraceNetworkRequest embraceNetworkRequest);

    /**
     * Logs a tap on a Compose screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     */
    void logComposeTap(@NonNull Pair<Float, Float> point, @NonNull String elementName);

    boolean shouldCaptureNetworkBody(@NonNull String url, @NonNull String method);

    void setProcessStartedByNotification();
}
