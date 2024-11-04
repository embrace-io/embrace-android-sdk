package io.embrace.android.exampleapp.ui.examples

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
fun SessionPropertiesExample() {
    var propKey by remember { mutableStateOf("my-session-property-key") }
    var propValue by remember { mutableStateOf("my-session-property-value") }
    var permValue by remember { mutableStateOf(false) }

    TextField(value = propKey, onValueChange = {
        propKey = it
    })
    TextField(value = propValue, onValueChange = {
        propValue = it
    })
    Row {
        Checkbox(
            checked = permValue,
            onCheckedChange = {
                permValue = it
            }
        )
        Text("Permanent property", Modifier.padding(top = 8.dp))
    }

    Spacer(Modifier.padding(8.dp))
    Button(onClick = {
        Embrace.getInstance().addSessionProperty(propKey, propValue, permValue)
    }) {
        Text("Add session property")
    }

    Button(onClick = {
        Embrace.getInstance().removeSessionProperty(propKey)
    }) {
        Text("Remove session property")
    }
}
