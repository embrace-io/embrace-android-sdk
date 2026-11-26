package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.spans.TracingApi

/**
 * The full Embrace SDK API, as exposed by the Embrace object.
 */
public interface SdkApi :
    LogsApi,
    NetworkRequestApi,
    UserSessionApi,
    UserApi,
    TracingApi,
    EmbraceAndroidApi,
    SdkLifecycleApi,
    SdkStateApi,
    OTelApi,
    BreadcrumbApi,
    InstrumentationApi,
    ExperimentApi
