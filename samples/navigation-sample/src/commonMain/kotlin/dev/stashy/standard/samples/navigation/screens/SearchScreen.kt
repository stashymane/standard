package dev.stashy.standard.samples.navigation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.stashy.navigation.compose.LocalNavigator
import dev.stashy.navigation.compose.rememberNavEntryState
import dev.stashy.standard.samples.navigation.Route
import dev.stashy.standard.samples.navigation.components.Screen

@Composable
internal fun SearchScreen() {
    val navigator = LocalNavigator.current
    var query by rememberNavEntryState { "" }
    val submit = { if (query.isNotBlank()) navigator.navigate(Route.SearchResults(query)) }

    Screen(title = "Search") {
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Query") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = submit,
            enabled = query.isNotBlank(),
        ) {
            Text("Search")
        }
    }
}
