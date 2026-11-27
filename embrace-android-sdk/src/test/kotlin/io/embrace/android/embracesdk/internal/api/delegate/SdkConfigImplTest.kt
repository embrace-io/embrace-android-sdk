package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.config.OTelConfig
import io.embrace.android.embracesdk.config.SdkConfig
import io.embrace.android.embracesdk.config.UserSessionConfig
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SdkConfigImplTest {

    private lateinit var otelConfig: ExternalOtelConfig
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var sdkConfig: SdkConfigImpl

    @Before
    fun setUp() {
        otelConfig = ExternalOtelConfig()
        errorHandler = FakeErrorHandler()
        sdkConfig = SdkConfigImpl(otelConfig, errorHandler)
    }

    @Test
    fun `config is applied`() {
        val spanExporter = FakeSpanExporter()
        val spanProcessor = FakeSpanProcessor()
        val logRecordExporter = FakeLogRecordExporter()
        val logRecordProcessor = FakeLogRecordProcessor()
        val firstListener = UserSessionListener { }
        val secondListener = UserSessionListener { }
        sdkConfig.configure {
            otel {
                addSpanExporter(spanExporter)
                addSpanProcessor(spanProcessor)
                addLogRecordExporter(logRecordExporter)
                addLogRecordProcessor(logRecordProcessor)
                setResourceAttribute("key", "value")
            }
            userSession {
                addListener(firstListener)
                addListener(secondListener)
                addListener(firstListener)
            }
        }
        assertEquals(listOf(firstListener, secondListener), sdkConfig.userSessionListeners)
        assertEquals(listOf(spanExporter), otelConfig.getSpanExporters())
        assertEquals(listOf(spanProcessor), otelConfig.getSpanProcessors())
        assertEquals(listOf(logRecordExporter), otelConfig.getLogExporters())
        assertEquals(listOf(logRecordProcessor), otelConfig.getLogRecordProcessors())
        assertEquals(mapOf("key" to "value"), otelConfig.getResourceAttributes())
        assertNoMessages()
    }

    @Test
    fun `exception thrown in a section is logged and the rest of the config still applies`() {
        val first = FakeSpanExporter()
        val second = FakeSpanExporter()
        val failure = IllegalStateException("section")
        sdkConfig.configure {
            otel {
                addSpanExporter(first)
                throw failure
            }
            otel {
                addSpanExporter(second)
            }
        }
        assertEquals(listOf(first, second), otelConfig.getSpanExporters())
        assertAppCodeFailureLogged(failure)
    }

    @Test
    fun `exception thrown by the config block is logged and earlier calls still apply`() {
        val exporter = FakeSpanExporter()
        val failure = IllegalStateException("block")
        sdkConfig.configure {
            otel {
                addSpanExporter(exporter)
            }
            throw failure
        }
        assertEquals(listOf(exporter), otelConfig.getSpanExporters())
        assertAppCodeFailureLogged(failure)
    }

    @Test
    fun `calls on config that escaped the block are dropped`() {
        lateinit var escapedSdkConfig: SdkConfig
        lateinit var escapedOTelConfig: OTelConfig
        lateinit var escapedUserSessionConfig: UserSessionConfig
        sdkConfig.configure {
            escapedSdkConfig = this
            otel {
                escapedOTelConfig = this
            }
            userSession {
                escapedUserSessionConfig = this
            }
        }
        escapedOTelConfig.addSpanExporter(FakeSpanExporter())
        escapedOTelConfig.setResourceAttribute("key", "value")
        escapedUserSessionConfig.addListener { }
        escapedSdkConfig.otel {
            addSpanProcessor(FakeSpanProcessor())
        }
        escapedSdkConfig.userSession {
            addListener { }
        }

        assertTrue(otelConfig.getSpanExporters().isEmpty())
        assertTrue(otelConfig.getSpanProcessors().isEmpty())
        assertTrue(otelConfig.getResourceAttributes().isEmpty())
        assertTrue(sdkConfig.userSessionListeners.isEmpty())
        assertEquals(
            listOf(
                "SDK config is only applied during Embrace.start(), cannot add_span_exporter.",
                "SDK config is only applied during Embrace.start(), cannot set_resource_attribute.",
                "SDK config is only applied during Embrace.start(), cannot add_user_session_listener.",
                "SDK config is only applied during Embrace.start(), cannot configure otel.",
                "SDK config is only applied during Embrace.start(), cannot configure user_session.",
            ),
            errorHandler.warningMessages.map { it.msg },
        )
    }

    private fun assertAppCodeFailureLogged(failure: Throwable) {
        val message = errorHandler.warningMessages.single()
        assertEquals("App-supplied code threw an exception (${SdkErrorType.PublicApiFail})", message.msg)
        assertEquals(failure, message.throwable)
    }

    private fun assertNoMessages() {
        assertTrue(errorHandler.warningMessages.isEmpty())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }
}
