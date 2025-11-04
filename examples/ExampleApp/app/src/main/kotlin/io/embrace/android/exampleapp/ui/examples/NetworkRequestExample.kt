package io.embrace.android.exampleapp.ui.examples

import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.exampleapp.ui.RadioButtonList
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

private enum class RequestType {
    GET_REQUEST,
    REDIRECTED_GET_REQUEST,
    POST_REQUEST,
    NOT_FOUND_REQUEST,
    INVALID_REQUEST
}

private val client = OkHttpClient.Builder().build()
private val hucExecutor = Executors.newFixedThreadPool(2)

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
                    Log.i("EmbraceTestApp", "Network request completed ${response.code} ${response.body.string()}")
                }
            })
        },
    ) {
        Text("Make OkHttp network request")
    }
    Button(
        onClick = {
            val url = getUrl(requestValue)
            hucExecutor.submit {
                var connection: HttpsURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpsURLConnection
                    connection.setRequestProperty("Content-Type", String.format("application/json;charset=%s", StandardCharsets.UTF_8))
                    connection.connectTimeout = 5000
                    connection.readTimeout = 15000

                    if (requestValue == RequestType.POST_REQUEST) {
                        connection.requestMethod = "POST"
                        connection.doInput = true
                        connection.doOutput = true
                        connection.outputStream.use { os ->
                            os.write("body".toByteArray(StandardCharsets.UTF_8))
                        }
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        Embrace.getInstance().addBreadcrumb("Successful request to $url")
                    } else {
                        Embrace.getInstance().addBreadcrumb("Non-successful request to $url with response code $responseCode")
                    }
                } catch (t: Throwable) {
                    Embrace.getInstance().addBreadcrumb("Error while making HUC network request: $t")
                } finally {
                    connection?.disconnect()
                }
            }
        },
    ) {
        Text("Make HUC network request")
    }
}

private fun getUrl(requestValue: RequestType): String = when (requestValue) {
    RequestType.GET_REQUEST -> "https://www.google.com"
    RequestType.REDIRECTED_GET_REQUEST -> "https://google.com"
    RequestType.POST_REQUEST -> "https://httpbin.org/post"
    RequestType.NOT_FOUND_REQUEST -> "https://httpbin.org/status/404"
    RequestType.INVALID_REQUEST -> "https://some-invalid-url.path"
}

private fun prepareRequest(requestType: RequestType): Request {
    val builder = Request.Builder().url(getUrl(requestType))
    if (requestType == RequestType.POST_REQUEST) {
        builder.post("{}".toRequestBody("application/json".toMediaType()))
    }
    return builder.build()
}
