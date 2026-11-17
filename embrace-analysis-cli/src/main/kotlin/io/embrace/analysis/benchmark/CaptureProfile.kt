package io.embrace.analysis.benchmark

/**
 * The perfetto config a trace was captured with, by the [key] the report prints. Two runs are comparable only if they
 * captured alike, and an absent dimension, such as scheduling, is a property of the capture rather than of the run.
 *
 * The first three are the profiles the macrobenchmark's `--capture` takes, and their keys are its keys
 * (`CaptureProfile` in `embrace-benchmark-common`, an Android library this module can't depend on);
 * `CaptureProfileClassifierTest` classifies that module's configs to hold the two equal. [ANDROIDX_DEFAULT] is
 * androidx.benchmark's own config, which the ExampleApp's benchmarks capture with, and [UNKNOWN] a trace that records
 * no config.
 */
enum class CaptureProfile(val key: String) {

    /** The app's own sections and counters, and nothing else: no scheduling. */
    APP_ONLY("app-only"),

    /** The sections plus every thread's scheduling, CPU frequency, binder, ART's sections and memory context. */
    WITH_CONTEXT("with-context"),

    /** The sections plus the app's resident set size and ART's heap size, without scheduling. */
    MEMORY("memory"),

    /** androidx.benchmark's default: every app's sections, many atrace categories, scheduling and memory. */
    ANDROIDX_DEFAULT("androidx-default"),

    /** The trace records no config. */
    UNKNOWN("unknown"),
}
