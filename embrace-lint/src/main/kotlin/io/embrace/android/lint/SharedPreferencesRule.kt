package io.embrace.android.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Forbids SharedPreferences as the SDK should prefer keeping state in other files.
 */
class SharedPreferencesRule : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = METHODS.keys.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val owners = METHODS[method.name] ?: return
        if (owners.any { context.evaluator.isMemberInSubClassOf(method, it) }) {
            context.report(ISSUE, node, context.getLocation(node), "`${method.name}` uses SharedPreferences. $ERR_MSG")
        }
    }

    companion object {
        private const val PREFS = "android.content.SharedPreferences"
        private const val EDITOR = "android.content.SharedPreferences.Editor"
        private const val CONTEXT = "android.content.Context"
        private const val ACTIVITY = "android.app.Activity"
        private const val PREFERENCE_MANAGER = "android.preference.PreferenceManager"
        private const val ANDROIDX_PREFERENCE_MANAGER = "androidx.preference.PreferenceManager"

        private val PREFS_OWNERS = listOf(PREFS)
        private val EDITOR_OWNERS = listOf(EDITOR)
        private val PREFERENCE_MANAGERS = listOf(PREFERENCE_MANAGER, ANDROIDX_PREFERENCE_MANAGER)

        private val METHODS: Map<String, List<String>> = mapOf(
            "getSharedPreferences" to listOf(CONTEXT),
            "deleteSharedPreferences" to listOf(CONTEXT),
            "moveSharedPreferencesFrom" to listOf(CONTEXT),
            "getPreferences" to listOf(ACTIVITY),
            "getDefaultSharedPreferences" to PREFERENCE_MANAGERS,
            "getDefaultSharedPreferencesName" to PREFERENCE_MANAGERS,
            "getAll" to PREFS_OWNERS,
            "getString" to PREFS_OWNERS,
            "getStringSet" to PREFS_OWNERS,
            "getInt" to PREFS_OWNERS,
            "getLong" to PREFS_OWNERS,
            "getFloat" to PREFS_OWNERS,
            "getBoolean" to PREFS_OWNERS,
            "contains" to PREFS_OWNERS,
            "edit" to PREFS_OWNERS,
            "registerOnSharedPreferenceChangeListener" to PREFS_OWNERS,
            "unregisterOnSharedPreferenceChangeListener" to PREFS_OWNERS,
            "putString" to EDITOR_OWNERS,
            "putStringSet" to EDITOR_OWNERS,
            "putInt" to EDITOR_OWNERS,
            "putLong" to EDITOR_OWNERS,
            "putFloat" to EDITOR_OWNERS,
            "putBoolean" to EDITOR_OWNERS,
            "remove" to EDITOR_OWNERS,
            "clear" to EDITOR_OWNERS,
            "commit" to EDITOR_OWNERS,
            "apply" to EDITOR_OWNERS,
        )

        const val ERR_MSG = "The SDK must not keep state in SharedPreferences. Only the prefs migration may read " +
            "them, with @Suppress(\"SharedPreferencesUsage\")."

        val ISSUE = Issue.create(
            id = "SharedPreferencesUsage",
            briefDescription = "SharedPreferences usage",
            explanation = ERR_MSG,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                SharedPreferencesRule::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
