package io.embrace.android.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

@Suppress("UnstableApiUsage")
class EmbracePublicApiPackageRuleTest : LintDetectorTest() {

    override fun getDetector(): Detector = EmbracePublicApiPackageRule()

    override fun getIssues(): MutableList<Issue> = mutableListOf(EmbracePublicApiPackageRule.ISSUE)

    @Test
    fun testRestrictedPackageJava() {
        lint().files(
            java(
                """
                package io.embrace.android.embracesdk;

                public class MyClass {}
                """
            )
        )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testRestrictedPackageKotlin() {
        lint().files(
            kotlin(
                """
                package io.embrace.android.embracesdk

                class MyClass
                """
            )
        )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testAllowedPackageJava() {
        lint().files(
            java(
                """
                package com.example.allowed;

                public class MyClass {}
                """
            )
        )
            .run()
            .expectClean()
    }

    @Test
    fun testAllowedPackageKotlin() {
        lint().files(
            kotlin(
                """
                package com.example.allowed

                class MyClass
                """
            )
        )
            .run()
            .expectClean()
    }
}
