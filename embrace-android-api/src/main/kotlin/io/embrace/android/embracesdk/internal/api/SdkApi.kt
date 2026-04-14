package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * @suppress
 */
@InternalApi
public interface SdkApi :
    LogsApi,
    NetworkRequestApi,
    UserSessionApi,
    UserApi,
    TracingApi,
    EmbraceAndroidApi,
    SdkStateApi,
    OTelApi,
    BreadcrumbApi,
    InstrumentationApi
