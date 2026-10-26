package io.embrace.android.gradle.plugin.buildreporter

import org.junit.Assert.assertEquals
import org.junit.Test

class JvmArgsRedactionTest {

    @Test
    fun `system properties are dropped`() {
        assertEquals("-Xmx4g", redactJvmArgs("-Xmx4g -Dhttps.proxyPassword=secret"))
    }

    @Test
    fun `heap and gc flags are kept`() {
        assertEquals(
            "-Xms512m -Xmx4g -XX:+UseParallelGC -XX:-UseG1GC",
            redactJvmArgs("  -Xms512m -Xmx4g -XX:+UseParallelGC -XX:-UseG1GC "),
        )
    }

    @Test
    fun `other jvm flags are dropped`() {
        assertEquals(
            "-Xmx2g",
            redactJvmArgs("-Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=1g -Dhttp.proxyHost=proxy.corp -Xmx2g"),
        )
    }

    @Test
    fun `empty args stay empty`() {
        assertEquals("", redactJvmArgs(""))
    }
}
