package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.EmbraceAndroidApi
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.api.SdkLifecycleApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * The calls an SDK instance serves: everything on [SdkApi] except the [SdkLifecycleApi] calls, which [SdkStateHolder]
 * handles itself, plus the internal interfaces.
 */
internal interface InternalSdkApi :
    LogsApi,
    NetworkRequestApi,
    UserSessionApi,
    UserApi,
    TracingApi,
    EmbraceAndroidApi,
    SdkStateApi,
    OTelApi,
    BreadcrumbApi,
    InstrumentationApi,
    ExperimentApi,
    InternalInterfaceApi
