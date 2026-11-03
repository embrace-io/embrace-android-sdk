package io.embrace.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

@Suppress("UnstableApiUsage")
class ContextDirAccessRuleTest : LintDetectorTest() {

    override fun getDetector(): Detector = ContextDirAccessRule()

    override fun getIssues(): MutableList<Issue> = mutableListOf(ContextDirAccessRule.ISSUE)

    @Test
    fun testKotlinProperties() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.Context

                fun dirs(context: Context) = listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir)
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(3)
    }

    @Test
    fun testKotlinMethods() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.Context

                fun open(context: Context) {
                    context.getDir("x", Context.MODE_PRIVATE)
                    context.openFileOutput("x", Context.MODE_PRIVATE)
                    context.openFileInput("x")
                    context.getFilesDir()
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(4)
    }

    @Test
    fun testApplicationSubclass() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.app.Application

                class App : Application() {
                    fun dir() = filesDir
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testJava() {
        lint().files(
            java(
                """
                package com.example;

                import android.content.Context;
                import java.io.File;

                class Dirs {
                    File dir(Context context) {
                        return context.getCacheDir();
                    }
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testDataDir() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.Context

                fun dir(context: Context) = context.applicationInfo.dataDir
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testSuppressed() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.Context

                @Suppress("EmbraceContextDirAccess")
                fun dir(context: Context) = context.filesDir
                """,
            ).indented(),
        )
            .run()
            .expectClean()
    }

    @Test
    fun testUnrelatedNames() {
        lint().files(
            kotlin(
                """
                package com.example

                class Storage(val filesDir: String, val dataDir: String) {
                    fun getCacheDir() = filesDir + dataDir
                }

                fun use(storage: Storage) = storage.filesDir + storage.getCacheDir()
                """,
            ).indented(),
        )
            .run()
            .expectClean()
    }
}
