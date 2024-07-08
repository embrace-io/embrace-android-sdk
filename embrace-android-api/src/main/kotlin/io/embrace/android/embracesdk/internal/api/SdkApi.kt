package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.spans.TracingApi

public interface SdkApi :
    LogsApi,
    MomentsApi,
    NetworkRequestApi,
    SessionApi,
    UserApi,
    TracingApi,
    EmbraceAndroidApi,
    SdkStateApi,
    OTelApi,
    InternalInterfaceApi,
    BreadcrumbApi,
    InternalWebViewApi
