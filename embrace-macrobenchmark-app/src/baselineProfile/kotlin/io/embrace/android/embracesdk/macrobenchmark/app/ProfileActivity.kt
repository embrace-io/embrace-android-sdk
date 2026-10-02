package io.embrace.android.embracesdk.macrobenchmark.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Runs [ProfileJourney] on the main thread, where apps mostly call the SDK, and shows its outcome.
 */
class ProfileActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outcome = try {
            ProfileJourney.run()
        } catch (exc: Exception) {
            "error ${exc.message ?: exc.javaClass.simpleName}"
        }
        setContentView(TextView(this).apply { text = "done: $outcome" })
    }
}
