package io.embrace.android.embracesdk.minified

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.minified.ExpectedName.Class
import io.embrace.android.embracesdk.minified.ExpectedName.Field
import io.embrace.android.embracesdk.minified.ExpectedName.Method
import io.embrace.android.embracesdk.minified.ExpectedName.Provider
import org.junit.Test
import org.junit.runner.RunWith

private const val SDK = "io.embrace.android.embracesdk"
private const val HUC_TRACKER = "$SDK.instrumentation.huc.HttpUrlConnectionTracker"

/**
 * Names the SDK resolves at runtime that cannot be derived from a source file, so
 * are listed by hand. Everything else is generated into expected_names.txt.
 */
private val MANUAL_NAMES = listOf(
    // public API
    Class("$SDK.Embrace"),
    Field("$SDK.Embrace", "INSTANCE"),
    Method("$SDK.Embrace", "start", 1),

    // SdkInitActions.initializeHucInstrumentation()
    Field(HUC_TRACKER, "INSTANCE"),
    Method(HUC_TRACKER, "registerUrlStreamHandlerFactory", 4),

    // OkHttpReflectionFacade
    Class("okhttp3.OkHttpClient"),
    Field("okhttp3.OkHttp", "VERSION"),

    // plugin config instrumentation
    Class("$SDK.internal.config.instrumented.schema.WebViewFragmentCapture"),
)

/**
 * Verifies that everything the SDK reaches via reflection or by name survives R8 when the app is
 * minified with only the SDK's consumer rules.
 */
@RunWith(AndroidJUnit4::class)
internal class KeepRulesTest {

    @Test
    fun namesResolvedByReflectionAreKept() {
        assertAllKept(MANUAL_NAMES)
    }

    @Test
    fun instrumentationProvidersAreKept() {
        assertAllKept(generatedExpectedNames().filterIsInstance<Provider>())
    }

    @Test
    fun gradlePluginHooksAreKept() {
        assertAllKept(generatedExpectedNames().filter { it !is Provider })
    }
}
