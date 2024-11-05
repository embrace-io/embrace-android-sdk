package io.embrace.android.exampleapp.ui.examples

import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.embrace.android.exampleapp.ui.RadioButtonList
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private enum class RequestType {
    GET_REQUEST,
    POST_REQUEST,
    NOT_FOUND_REQUEST,
    INVALID_REQUEST
}

private val client = OkHttpClient.Builder().build()

@Composable
fun NetworkRequestExample() {
    var requestValue by remember { mutableStateOf(RequestType.GET_REQUEST) }

    RadioButtonList(
        items = RequestType.entries,
        selectedItem = requestValue
    ) {
        requestValue = it
    }
    Button(
        onClick = {
            val request = prepareRequest(requestValue)
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.i("EmbraceTestApp", "Network request failed ${e.message}")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    Log.i("EmbraceTestApp", "Network request completed ${response.code} ${response.body?.string()}")
                }
            })
        },
    ) {
        Text("Make network request")
    }
}

private fun prepareRequest(requestType: RequestType): Request {
    val builder = Request.Builder()

    when (requestType) {
        RequestType.GET_REQUEST -> builder.url("https://httpbin.org/get")
        RequestType.POST_REQUEST -> builder.url("https://httpbin.org/post")
        RequestType.NOT_FOUND_REQUEST -> builder.url("https://httpbin.org/status/404")
        RequestType.INVALID_REQUEST -> builder.url("https://some-invalid-url.path")
    }
    if (requestType == RequestType.POST_REQUEST) {
        builder.post("{}".toRequestBody("application/json".toMediaType()))
    }
    return builder.build()
}
