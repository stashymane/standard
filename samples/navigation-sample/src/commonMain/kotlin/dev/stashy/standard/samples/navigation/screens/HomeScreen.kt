package dev.stashy.standard.samples.navigation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stashy.navigation.compose.LocalNavigator
import dev.stashy.standard.samples.navigation.Route
import dev.stashy.standard.samples.navigation.components.Screen

@Composable
internal fun HomeScreen() {
    val navigator = LocalNavigator.current
    val articles = remember {
        listOf(
            "intro" to "Introduction to Navigation",
            "compose" to "Compose for Web",
            "state" to "Managing State",
            "animations" to "Navigation Animations",
            "deep-links" to "Deep Linking",
        )
    }

    Screen(title = "Home") {
        Text("Featured articles", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(articles) { (slug, title) ->
                Card(
                    onClick = { navigator.navigate(Route.Article(slug)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(title, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
