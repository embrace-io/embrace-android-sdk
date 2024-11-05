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

@Composable
fun JvmCrashExample() {
    var textValue by remember { mutableStateOf("My crash message") }
    TextField(value = textValue, onValueChange = {
        textValue = it
    })
    Spacer(Modifier.padding(8.dp))
    Button(onClick = {
        throw IllegalStateException(textValue)
    }) {
        Text("Trigger JVM crash")
    }
}
