package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

@Suppress("DEPRECATION")
internal class PublicEnumAliasTest {

    @Test
    fun `deprecated SCREAMING_CASE aliases resolve to UpperCamelCase entries`() {
        listOf(
            LastRunEndState.INVALID to LastRunEndState.Invalid,
            LastRunEndState.CRASH to LastRunEndState.Crash,
            LastRunEndState.CLEAN_EXIT to LastRunEndState.CleanExit,
            PropertyScope.USER_SESSION to PropertyScope.UserSession,
            PropertyScope.PROCESS to PropertyScope.Process,
            PropertyScope.PERMANENT to PropertyScope.Permanent,
            Severity.INFO to Severity.Info,
            Severity.WARNING to Severity.Warning,
            Severity.ERROR to Severity.Error,
            AutoTerminationMode.NONE to AutoTerminationMode.None,
            AutoTerminationMode.ON_BACKGROUND to AutoTerminationMode.OnBackground,
            ErrorCode.FAILURE to ErrorCode.Failure,
            ErrorCode.USER_ABANDON to ErrorCode.UserAbandon,
            ErrorCode.UNKNOWN to ErrorCode.Unknown,
            HttpMethod.GET to HttpMethod.Get,
            HttpMethod.HEAD to HttpMethod.Head,
            HttpMethod.POST to HttpMethod.Post,
            HttpMethod.PUT to HttpMethod.Put,
            HttpMethod.DELETE to HttpMethod.Delete,
            HttpMethod.CONNECT to HttpMethod.Connect,
            HttpMethod.OPTIONS to HttpMethod.Options,
            HttpMethod.TRACE to HttpMethod.Trace,
            HttpMethod.PATCH to HttpMethod.Patch,
        ).forEach { (old, new) -> assertSame(new, old) }
    }

    @Test
    fun `deprecated aliases are static fields for binary compatibility`() {
        listOf(
            LastRunEndState::class.java to "CLEAN_EXIT",
            PropertyScope::class.java to "USER_SESSION",
            Severity::class.java to "WARNING",
            AutoTerminationMode::class.java to "ON_BACKGROUND",
            ErrorCode::class.java to "USER_ABANDON",
            HttpMethod::class.java to "OPTIONS",
        ).forEach { (clz, name) ->
            val field = clz.getField(name)
            assertSame(clz, field.type)
            assertTrue(Modifier.isStatic(field.modifiers))
        }
    }
}
