package io.embrace.android.exampleapp

import android.app.Application
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import io.embrace.android.embracesdk.Embrace

class MainApplication: Application() {

    companion object {
        init {
            System.loadLibrary("emb-samples")
        }
    }

    override fun onCreate() {
        super.onCreate()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // Get new FCM registration token
            if (task.isSuccessful) {
                Log.d("MainApplication", "FCM token: ${task.result}")
            }
        }

        // add OTel exporters to send data to 3rd party destinations
        Embrace.getInstance().addSpanExporter(LogcatSpanExporter())
        Embrace.getInstance().addLogRecordExporter(LogcatLogRecordExporter())

        // start embrace SDK
        Embrace.getInstance().start(this)
    }
}
