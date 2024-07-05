package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.spans.TracingApi

internal interface SdkApi :
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
