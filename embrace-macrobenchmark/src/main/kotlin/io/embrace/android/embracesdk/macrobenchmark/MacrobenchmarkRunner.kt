package io.embrace.android.embracesdk.macrobenchmark

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

class MacrobenchmarkRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle) {
        arguments.putString("androidx.benchmark.enabledRules", "Macrobenchmark")
        super.onCreate(arguments)
    }
}
