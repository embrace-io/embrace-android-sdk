package io.embrace.android.embracesdk.internal.capture.activity

/**
 * Interface handles all the data, automatically captured or custom-added, required to generate UI Load traces
 */
interface UiLoadDataListener : UiLoadEventListener, UiLoadTraceModifier
