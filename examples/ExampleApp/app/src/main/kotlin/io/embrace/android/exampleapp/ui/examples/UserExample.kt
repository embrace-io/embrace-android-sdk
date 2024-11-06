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
fun UserExample() {
    var usernameValue by remember { mutableStateOf("my-user-name") }
    var userEmailValue by remember { mutableStateOf("fake@example.com") }
    var userIdentifierValue by remember { mutableStateOf("my-user-id") }

    Spacer(Modifier.padding(8.dp))
    TextField(value = usernameValue, onValueChange = {
        usernameValue = it
    })
    Spacer(Modifier.padding(4.dp))
    Button(onClick = {
        Embrace.getInstance().setUsername(usernameValue)
    }) {
        Text("Set username")
    }
    Button(onClick = {
        Embrace.getInstance().clearUsername()
    }) {
        Text("Clear username")
    }

    Spacer(Modifier.padding(8.dp))
    TextField(value = userEmailValue, onValueChange = {
        userEmailValue = it
    })
    Spacer(Modifier.padding(4.dp))

    Button(onClick = {
        Embrace.getInstance().setUserEmail(userEmailValue)
    }) {
        Text("Set user email")
    }
    Button(onClick = {
        Embrace.getInstance().clearUserEmail()
    }) {
        Text("Clear user email")
    }

    Spacer(Modifier.padding(8.dp))
    TextField(value = userIdentifierValue, onValueChange = {
        userIdentifierValue = it
    })
    Spacer(Modifier.padding(4.dp))

    Button(onClick = {
        Embrace.getInstance().setUserIdentifier(userEmailValue)
    }) {
        Text("Set user ID")
    }
    Button(onClick = {
        Embrace.getInstance().clearUserIdentifier()
    }) {
        Text("Clear user ID")
    }
}
