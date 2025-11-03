package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrSampleList

/**
 * Retrieves the size of the list.
 */
internal fun AnrSampleList.size(): Int = samples.size
