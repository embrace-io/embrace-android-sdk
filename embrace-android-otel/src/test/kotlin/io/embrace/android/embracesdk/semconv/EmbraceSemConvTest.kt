package io.embrace.android.embracesdk.semconv

import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSemconv::class)
class EmbraceSemConvTest {

    @Test
    fun `attributes defined in the local registry are generated and accessible`() {
        assertEquals("emb.session_number", EmbSessionAttributes.EMB_SESSION_NUMBER)
        assertEquals("emb.android.native_crash.symbols", EmbAndroidAttributes.EMB_ANDROID_NATIVE_CRASH_SYMBOLS)
        assertEquals("emb.manual_instrumentation", EmbCommonAttributes.EMB_MANUAL_INSTRUMENTATION)
    }

    @Test
    fun `attributes defined in the shared federated registry are generated and accessible`() {
        assertEquals("emb.user_session_id", EmbSessionAttributes.EMB_USER_SESSION_ID)
    }
}
