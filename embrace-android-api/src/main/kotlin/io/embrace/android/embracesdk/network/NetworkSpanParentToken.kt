package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Identifies one call to setNetworkSpanParent so that only that call's network span parent can be cleared.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
public interface NetworkSpanParentToken
