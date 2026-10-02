@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.macrobenchmark.app

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.otel.java.getJavaOpenTelemetry
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.api.logs.Logger as JavaLogger
import io.opentelemetry.api.logs.Severity as JavaSeverity
import io.opentelemetry.api.trace.Tracer as JavaTracer
import io.opentelemetry.context.Context as JavaContext

/**
 * Drives the core APIs an app calls at runtime, so that the baseline profile covers them as well
 * as SDK init.
 */
object ProfileJourney {

    /**
     * Returns the outcome shown after `done: `, which the generator requires to be `ok`.
     */
    fun run(): String {
        if (!Embrace.isStarted) {
            return "sdk-not-started"
        }
        val kotlinOtel = Embrace.getOpenTelemetryKotlin()
        val tracer = kotlinOtel.tracerProvider.getTracer(INSTRUMENTATION_NAME)
        val logger = kotlinOtel.loggerProvider.getLogger(INSTRUMENTATION_NAME)
        val javaOtel = Embrace.getJavaOpenTelemetry()
        val javaTracer = javaOtel.getTracer(INSTRUMENTATION_NAME)
        val javaLogger = javaOtel.logsBridge.get(INSTRUMENTATION_NAME)

        repeat(ITERATIONS) {
            userAndSession(it)
            embraceSpans(it)
            embraceLogs(it)
            kotlinApi(it, kotlinOtel, tracer, logger)
            javaApi(it, javaTracer, javaLogger)
        }

        val inFlight = List(IN_FLIGHT_SPANS) { Embrace.startSpan("profile-in-flight-$it") }
        val previousSessionId = Embrace.currentUserSessionId
        Embrace.endUserSession()
        inFlight.forEach { it.stop() }
        return if (Embrace.currentUserSessionId == previousSessionId) "end-declined" else "ok"
    }

    @Suppress("DEPRECATION")
    private fun userAndSession(iteration: Int) {
        val key = "profile-${iteration % DISTINCT_KEYS}"
        Embrace.setUserIdentifier("profile-user-$iteration")
        Embrace.setUsername("profile-username")
        Embrace.setUserEmail("profile@example.com")
        Embrace.addUserPersona(key)
        Embrace.clearUserPersona(key)
        Embrace.addUserSessionProperty(key, "value-$iteration", PropertyScope.USER_SESSION)
        Embrace.addUserSessionProperty("$key-permanent", "value", PropertyScope.PERMANENT)
        Embrace.removeUserSessionProperty("$key-permanent")
        Embrace.trackExperiment(key, "variant-a")
        Embrace.trackFeatureFlag(key, "on")
        Embrace.untrackFeatureFlag(key)
    }

    private fun embraceSpans(iteration: Int) {
        val attributes = mapOf("iteration" to iteration.toString())

        val parent = Embrace.createSpan("profile-parent")
        parent.start()
        parent.addAttribute("iteration", iteration.toString())
        parent.addEvent("profile-plain-event")

        val child = Embrace.startSpan("profile-child", parent)
        child.addEvent("profile-event", System.currentTimeMillis(), attributes)
        child.updateName("profile-child-renamed")
        child.addLink(parent, attributes)
        child.recordException(IllegalStateException("profile"), attributes)
        child.stop(ErrorCode.FAILURE)

        parent.spanId?.let(Embrace::getSpan)
        Embrace.recordSpan("profile-recorded", parent, attributes) { iteration * 2 }

        val now = System.currentTimeMillis()
        Embrace.recordCompletedSpan(
            name = "profile-completed",
            startTimeMs = now - 10,
            endTimeMs = now,
            parent = parent,
            attributes = attributes,
            events = listOfNotNull(EmbraceSpanEvent.create("profile-event", now - 5, attributes)),
        )
        parent.stop()
    }

    private fun embraceLogs(iteration: Int) {
        val properties = mapOf("iteration" to iteration.toString())
        val exception = IllegalStateException("profile")

        Embrace.logInfo("profile info")
        Embrace.logWarning("profile warning")
        Embrace.logError("profile error")
        Embrace.logMessage("profile message", Severity.WARNING, properties)
        Embrace.logException(exception, Severity.ERROR, properties, "profile exception")
        Embrace.logCustomStacktrace(exception.stackTrace, Severity.ERROR, properties, "profile stacktrace")
        Embrace.addBreadcrumb("profile breadcrumb $iteration")
    }

    private fun kotlinApi(iteration: Int, otel: OpenTelemetry, tracer: Tracer, logger: Logger) {
        val parent = tracer.startSpan("otel-kotlin-parent") {
            setLongAttribute("iteration", iteration.toLong())
        }
        parent.setStringAttribute("key", "value")
        parent.addEvent("otel-kotlin-event") {
            setBooleanAttribute("flag", true)
        }

        val child = tracer.startSpan("otel-kotlin-child", otel.context.root().storeSpan(parent)) {
            addLink(parent.spanContext)
        }
        child.setStatus(StatusData.Error("profile"))
        child.end()

        val scope = otel.context.implicit().storeSpan(parent).attach()
        tracer.startSpan("otel-kotlin-implicit-child").end()
        logger.emit(body = "otel-kotlin log in span", severityNumber = SeverityNumber.INFO) {
            setStringAttribute("key", "value")
        }
        scope.detach()

        logger.emit(body = "otel-kotlin log", severityNumber = SeverityNumber.WARN)
        parent.setStatus(StatusData.Ok)
        parent.end()
    }

    private fun javaApi(iteration: Int, tracer: JavaTracer, logger: JavaLogger) {
        val parent = tracer.spanBuilder("otel-java-parent").setAttribute("iteration", iteration.toLong()).startSpan()
        parent.makeCurrent().use {
            val child = tracer.spanBuilder("otel-java-child").setParent(JavaContext.current().with(parent)).startSpan()
            child.setAttribute("key", "value")
            child.addEvent("otel-java-event", Attributes.of(AttributeKey.stringKey("key"), "value"))
            child.end()

            logger.logRecordBuilder()
                .setBody("otel-java log in span")
                .setSeverity(JavaSeverity.WARN)
                .setAttribute(AttributeKey.stringKey("key"), "value")
                .emit()
        }
        parent.end()
    }

    private const val INSTRUMENTATION_NAME = "profile-journey"
    private const val ITERATIONS = 25
    private const val DISTINCT_KEYS = 5
    private const val IN_FLIGHT_SPANS = 5
}
