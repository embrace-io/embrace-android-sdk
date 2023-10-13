package io.embrace.android.embracesdk;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.http.HttpMethod;
import io.embrace.android.embracesdk.network.http.NetworkCaptureData;

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their
 * sole source of communication with the Android SDK.
 */
interface EmbraceInternalInterface {

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
     * {@see Embrace#logBreadcrumb}
     */
    void addBreadcrumb(@NonNull String message);

    /**
     * {@see Embrace#getDeviceId}
     */
    @NonNull
    String getDeviceId();

    /**
     * {@see Embrace#setUsername}
     */
    void setUsername(@Nullable String username);

    /**
     * {@see Embrace#clearUsername}
     */
    void clearUsername();

    /**
     * {@see Embrace#setUserIdentifier}
     */
    void setUserIdentifier(@Nullable String userId);

    /**
     * {@see Embrace#clearUserIdentifier}
     */
    void clearUserIdentifier();

    /**
     * {@see Embrace#setUserEmail}
     */
    void setUserEmail(@Nullable String email);

    /**
     * {@see Embrace#clearUserEmail}
     */
    void clearUserEmail();

    /**
     * {@see Embrace#setUserAsPayer}
     */
    void setUserAsPayer();

    /**
     * {@see Embrace#clearUserAsPayer}
     */
    void clearUserAsPayer();

    /**
     * {@see Embrace#addUserPersona}
     */
    void addUserPersona(@NonNull String persona);

    /**
     * {@see Embrace#clearUserPersona}
     */
    void clearUserPersona(@NonNull String persona);

    /**
     * {@see Embrace#clearAllUserPersonas}
     */
    void clearAllUserPersonas();

    /**
     * {@see Embrace#addSessionProperty}
     */
    boolean addSessionProperty(@NonNull String key,
                               @NonNull String value,
                               boolean permanent);

    /**
     * {@see Embrace#removeSessionProperty}
     */
    boolean removeSessionProperty(@NonNull String key);

    /**
     * {@see Embrace#getSessionProperties}
     */
    @Nullable
    Map<String, String> getSessionProperties();

    /**
     * {@see Embrace#startEvent}
     */
    void startMoment(@NonNull String name,
                     @Nullable String identifier,
                     @Nullable Map<String, Object> properties);

    /**
     * {@see Embrace#endMoment}
     */
    void endMoment(@NonNull String name,
                   @Nullable String identifier,
                   @Nullable Map<String, Object> properties);

    /**
     * {@see Embrace#startFragment}
     */
    boolean startView(@NonNull String name);

    /**
     * {@see Embrace#endFragment}
     */
    boolean endView(@NonNull String name);

    /**
     * {@see Embrace#endAppStartup}
     */
    void endAppStartup(@NonNull Map<String, Object> properties);

    /**
     * {@see Embrace#logInternalError}
     */
    void logInternalError(@Nullable String message, @Nullable String details);

    /**
     * {@see Embrace#endSession}
     */
    void endSession(boolean clearUserInfo);

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
     * Logs a tap on a Compose screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     */
    void logComposeTap(@NonNull Pair<Float, Float> point, @NonNull String elementName);
}
