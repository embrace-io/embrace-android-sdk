package io.embrace.android.exampleapp.ui.examples

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.embrace.android.embracesdk.Embrace

@Composable
fun ViewTrackingExample() {
    var textValue by remember { mutableStateOf("my-view-name") }
    var started by remember { mutableStateOf(false) }
    TextField(value = textValue, onValueChange = {
        textValue = it
    })
    Spacer(Modifier.padding(8.dp))
    Button(onClick = {
        if (started) {
            Embrace.getInstance().endView(textValue)
            started = false
        } else {
            Embrace.getInstance().startView(textValue)
            started = true
        }
    }) {
        val msg = when {
            started -> "End View"
            else -> "Start View"
        }
        Text(msg)
    }
}
