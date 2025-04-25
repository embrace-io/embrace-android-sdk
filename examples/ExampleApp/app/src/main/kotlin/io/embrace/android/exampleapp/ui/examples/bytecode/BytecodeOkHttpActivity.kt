package io.embrace.android.exampleapp.ui.examples.bytecode

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import io.embrace.android.exampleapp.R
import okhttp3.OkHttpClient
import okhttp3.Request

class BytecodeOkHttpActivity : ComponentActivity() {

    private val client = OkHttpClient.Builder().build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bytecode_okhttp)

        findViewById<View>(R.id.btn_bytecode_okhttp).setOnClickListener {
            client.newCall(Request.Builder().url("https://embrace.io/").build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.i("EmbraceTestApp", "Network request failed ${e.message}")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    Log.i("EmbraceTestApp", "Network request completed ${response.code}")
                }
            })
        }
    }
}
