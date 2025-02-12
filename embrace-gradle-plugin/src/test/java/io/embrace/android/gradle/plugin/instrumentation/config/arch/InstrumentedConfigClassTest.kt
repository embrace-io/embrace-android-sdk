package io.embrace.android.gradle.plugin.instrumentation.config.arch

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import io.embrace.android.gradle.plugin.instrumentation.config.BooleanReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.IntReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.LongReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.MapReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringReturnValueMethodVisitor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.objectweb.asm.MethodVisitor

class InstrumentedConfigClassTest {

    private val nextVisitor: MethodVisitor = mockk(relaxed = true)
    private val api = ASM_API_VERSION

    private val cfg = modelSdkConfigClass {
        boolMethod("getBool") { true }
        boolMethod("getNullBool") { null }
        intMethod("getInt") { 5 }
        intMethod("getNullInt") { null }
        longMethod("getLong") { 150L }
        longMethod("getNullLong") { null }
        stringMethod("getString") { "hello" }
        stringMethod("getNullString") { null }
        stringListMethod("getStringList") { listOf("hello") }
        stringListMethod("getNullStringList") { null }
        mapMethod("getMap") { mapOf("a" to "1") }
        mapMethod("getNullMap") { null }
    }

    @Test
    fun `test bool`() {
        val visitor = cfg.getMethodVisitor("getBool", "()Z", api, nextVisitor)
        assertEquals(true, (visitor as BooleanReturnValueMethodVisitor).replacedValue)

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()Z", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getBool", "()J", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullBool", "()Z", api, nextVisitor))
    }

    @Test
    fun `test int`() {
        val visitor = cfg.getMethodVisitor("getInt", "()I", api, nextVisitor)
        assertEquals(5, (visitor as IntReturnValueMethodVisitor).replacedValue)

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()I", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getInt", "()Z", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullInt", "()I", api, nextVisitor))
    }

    @Test
    fun `test long`() {
        val visitor = cfg.getMethodVisitor("getLong", "()J", api, nextVisitor)
        assertEquals(150L, (visitor as LongReturnValueMethodVisitor).replacedValue)

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()J", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getLong", "()Z", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullLong", "()J", api, nextVisitor))
    }

    @Test
    fun `test string`() {
        val visitor = cfg.getMethodVisitor("getString", "()Ljava/lang/String;", api, nextVisitor)
        assertEquals("hello", (visitor as StringReturnValueMethodVisitor).replacedValue)

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()Ljava/lang/String;", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getString", "()Z", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullString", "()Ljava/lang/String;", api, nextVisitor))
    }

    @Test
    fun `test string list`() {
        val visitor = cfg.getMethodVisitor("getStringList", "()Ljava/util/List;", api, nextVisitor)
        assertEquals(
            listOf("hello"),
            (visitor as io.embrace.android.gradle.plugin.instrumentation.config.StringListReturnValueMethodVisitor).replacedValue
        )

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()Ljava/util/List;", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getStringList", "()Z", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullStringList", "()Ljava/util/List;", api, nextVisitor))
    }

    @Test
    fun `test map`() {
        val visitor = cfg.getMethodVisitor("getMap", "()Ljava/util/Map;", api, nextVisitor)
        assertEquals(mapOf("a" to "1"), (visitor as MapReturnValueMethodVisitor).replacedValue)

        // name doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("someFunction", "()Ljava/util/Map;", api, nextVisitor))

        // descriptor doesn't match
        assertSame(nextVisitor, cfg.getMethodVisitor("getMap", "()Z", api, nextVisitor))

        // no config value supplied
        assertSame(nextVisitor, cfg.getMethodVisitor("getNullMap", "()Ljava/util/Map;", api, nextVisitor))
    }
}
