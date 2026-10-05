package io.embrace.android.embracesdk.macrobenchmark

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

class MacrobenchmarkRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle) {
        if (arguments.getString(ENABLED_RULES) == null) {
            arguments.putString(ENABLED_RULES, "Macrobenchmark")
        }
        super.onCreate(arguments)
    }

    private companion object {
        const val ENABLED_RULES = "androidx.benchmark.enabledRules"
    }
}
