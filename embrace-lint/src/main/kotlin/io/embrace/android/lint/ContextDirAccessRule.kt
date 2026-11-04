package io.embrace.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

/**
 * Forbids resolving app directories from a Context so every SDK file goes through SdkStorage.
 */
class ContextDirAccessRule : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = CONTEXT_METHODS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)) {
            report(context, node, method.name)
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(DATA_DIR)

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField && referenced.containingClass?.qualifiedName == APPLICATION_INFO_CLASS) {
            report(context, reference, DATA_DIR)
        }
    }

    private fun report(context: JavaContext, node: UElement, name: String) {
        context.report(ISSUE, node, context.getLocation(node), "`$name` bypasses SdkStorage. $ERR_MSG")
    }

    companion object {
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val APPLICATION_INFO_CLASS = "android.content.pm.ApplicationInfo"
        private const val DATA_DIR = "dataDir"

        private val CONTEXT_METHODS = setOf(
            "getFilesDir",
            "getCacheDir",
            "getNoBackupFilesDir",
            "getDir",
            "openFileOutput",
            "openFileInput",
        )

        const val ERR_MSG = "Resolve SDK files through SdkStorage. Only storage factories, migrators and " +
            "host disk measurement may read Context directories, with @Suppress(\"EmbraceContextDirAccess\")."

        val ISSUE = Issue.create(
            id = "EmbraceContextDirAccess",
            briefDescription = "Direct Context directory access",
            explanation = ERR_MSG,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                ContextDirAccessRule::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
