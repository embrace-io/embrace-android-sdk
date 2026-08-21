package io.embrace.android.embracesdk.internal.capture.experiment

/**
 * Different types of records that the Experiments API supports. Not used by the SDK to fork behavior, but instead is a classification
 * that passes the user intent to the backend so it can route and do things differently (e.g. show them on different dashboard pages)
 */
enum class ExperimentKind(val code: String) {
    EXPERIMENT("e"),
    FEATURE_FLAG("f"),
}
