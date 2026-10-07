package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.EmbraceAndroidApi
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * The Embrace SDK API, implemented by the `Embrace` object.
 *
 * No function on this interface throws. Before the SDK has started, calls that must survive start-up are buffered
 * and every other call is dropped. If the SDK is disabled or fails to start, every call is a no-op.
 */
public interface EmbraceSdk :
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
    ExperimentApi
