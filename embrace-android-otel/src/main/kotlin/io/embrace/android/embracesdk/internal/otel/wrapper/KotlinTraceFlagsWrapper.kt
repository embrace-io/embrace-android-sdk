package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.tracing.TraceFlags

class KotlinTraceFlagsWrapper(
    private val impl: TraceFlags,
) : OtelJavaTraceFlags {
    override fun isSampled(): Boolean = impl.isSampled

    // FIXME: temporary. requires change in opentelemetry-kotlin first
    override fun asHex(): String = throw UnsupportedOperationException()

    override fun asByte(): Byte = asHex().toByte()
}
