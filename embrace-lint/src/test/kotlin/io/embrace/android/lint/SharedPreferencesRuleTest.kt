package io.embrace.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

@Suppress("UnstableApiUsage")
class SharedPreferencesRuleTest : LintDetectorTest() {

    override fun getDetector(): Detector = SharedPreferencesRule()

    override fun getIssues(): MutableList<Issue> = mutableListOf(SharedPreferencesRule.ISSUE)

    @Test
    fun testAcquisition() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.app.Activity
                import android.content.Context
                import android.preference.PreferenceManager

                fun open(context: Context, activity: Activity) {
                    context.getSharedPreferences("x", Context.MODE_PRIVATE)
                    context.deleteSharedPreferences("x")
                    activity.getPreferences(Context.MODE_PRIVATE)
                    PreferenceManager.getDefaultSharedPreferences(context)
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(4)
    }

    @Test
    fun testReadsAndWrites() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.SharedPreferences

                fun use(prefs: SharedPreferences) {
                    prefs.getString("a", null)
                    prefs.all
                    prefs.edit().putInt("b", 1).apply()
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(5)
    }

    @Test
    fun testJava() {
        lint().files(
            java(
                """
                package com.example;

                import android.content.SharedPreferences;

                class Prefs {
                    boolean write(SharedPreferences prefs) {
                        return prefs.edit().remove("a").commit();
                    }
                }
                """,
            ).indented(),
        )
            .run()
            .expectErrorCount(3)
    }

    @Test
    fun testSuppressed() {
        lint().files(
            kotlin(
                """
                package com.example

                import android.content.SharedPreferences

                @Suppress("SharedPreferencesUsage")
                fun read(prefs: SharedPreferences) = prefs.getString("a", null)
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

                class Store {
                    fun getString(key: String): String = key
                    fun edit(): Store = this
                    fun apply() {}
                }

                fun use(store: Store, map: MutableMap<String, String>) {
                    store.getString("a")
                    store.edit().apply()
                    map.remove("a")
                    map.clear()
                    StringBuilder().apply { append("a") }
                }
                """,
            ).indented(),
        )
            .run()
            .expectClean()
    }
}
