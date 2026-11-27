package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * @suppress
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
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
