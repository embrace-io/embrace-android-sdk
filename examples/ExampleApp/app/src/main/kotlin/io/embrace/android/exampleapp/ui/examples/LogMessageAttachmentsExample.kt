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
import io.embrace.android.embracesdk.Severity
import io.embrace.android.exampleapp.ui.RadioButtonList
import java.util.UUID

@Composable
fun LogMessageAttachmentsExample() {
    var textValue by remember { mutableStateOf("My log message") }
    var severityValue by remember { mutableStateOf(Severity.INFO) }
    var includeProps by remember { mutableStateOf(true) }
    var hostFileOn3rdPartyServer by remember { mutableStateOf(false) }

    TextField(value = textValue, onValueChange = {
        textValue = it
    })
    RadioButtonList(
        items = Severity.entries,
        selectedItem = severityValue
    ) {
        severityValue = it
    }

    Row {
        Checkbox(
            checked = includeProps,
            onCheckedChange = {
                includeProps = it
            }
        )
        Text("Include properties", Modifier.padding(top = 8.dp))
    }

    Row {
        Checkbox(
            checked = hostFileOn3rdPartyServer,
            onCheckedChange = {
                hostFileOn3rdPartyServer = it
            }
        )
        Text("Host files on a 3rd party server (not Embrace)", Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.padding(8.dp))
    Button(onClick = {
        TODO("Not supported in 7.0.")
    }) {
        Text("Send Log")
    }
}
