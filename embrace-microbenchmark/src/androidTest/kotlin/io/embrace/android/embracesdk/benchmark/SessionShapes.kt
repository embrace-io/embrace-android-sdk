package io.embrace.android.embracesdk.benchmark

/**
 * The session sizes the persistence benchmarks run against, as (completed spans, attributes per
 * span) pairs in the form JUnit's parameterized runner takes.
 */
internal val SESSION_SHAPES: List<Array<Any>> = listOf(
    arrayOf(10, 5),
    arrayOf(100, 5),
    arrayOf(1_000, 5),
    arrayOf(3_998, 5),
    arrayOf(100, 50),
    arrayOf(500, 50),
)
