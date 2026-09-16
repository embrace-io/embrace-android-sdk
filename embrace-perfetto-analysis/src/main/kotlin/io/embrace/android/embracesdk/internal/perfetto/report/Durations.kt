package io.embrace.android.embracesdk.internal.perfetto.report

import java.util.Locale

private const val NANOS_PER_MICRO = 1000.0
private const val MICROS_FORMAT = "%.3f"
private const val PERCENT_FORMAT = "%.4f"

internal fun micros(nanos: Long): String = micros(nanos.toDouble())

internal fun micros(nanos: Double): String = String.format(Locale.ROOT, MICROS_FORMAT, nanos / NANOS_PER_MICRO)

internal fun percent(value: Double): String = String.format(Locale.ROOT, PERCENT_FORMAT, value)
