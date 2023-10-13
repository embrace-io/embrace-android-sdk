package io.embrace.android.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass

/**
 * Checks for classes in the io.embrace.android.embracesdk package and warns against
 * adding new ones.
 */
@Suppress("UnstableApiUsage")
class EmbracePublicApiPackageRule : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UClass>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val packageName = context.uastFile?.packageName
                if (packageName == RESTRICTED_PACKAGE_NAME) {
                    context.report(
                        issue = ISSUE,
                        location = context.getNameLocation(node),
                        message = ERR_MSG,
                    )
                }
            }
        }
    }

    companion object {
        const val RESTRICTED_PACKAGE_NAME = "io.embrace.android.embracesdk"
        const val ERR_MSG = "Don't put classes in the $RESTRICTED_PACKAGE_NAME package unless " +
            "they're part of the public API. Please move the new class to an appropriate " +
            "package or (if you're adding to the public API) suppress this error " +
            "via the lint baseline file."

        val ISSUE = Issue.create(
            id = "EmbracePublicApiPackageRule",
            briefDescription = "Use of default package",
            explanation = ERR_MSG,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                EmbracePublicApiPackageRule::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
