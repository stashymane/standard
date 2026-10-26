package dev.stashy.standard.samples.navigation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stashy.navigation.compose.LocalNavigator
import dev.stashy.standard.samples.navigation.Route
import dev.stashy.standard.samples.navigation.components.Screen

@Composable
internal fun SearchResultsScreen(query: String) {
    val navigator = LocalNavigator.current
    val results = remember(query) { List(5) { "$query result ${it + 1}" } }

    Screen(title = "Results for \"$query\"") {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(results) { result ->
                Card(
                    onClick = { navigator.navigate(Route.Article(result.replace(" ", "-"))) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(result, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
