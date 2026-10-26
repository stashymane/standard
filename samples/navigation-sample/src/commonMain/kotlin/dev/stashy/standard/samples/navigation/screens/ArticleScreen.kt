package dev.stashy.standard.samples.navigation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stashy.navigation.compose.LocalNavigator
import dev.stashy.standard.samples.navigation.Route
import dev.stashy.standard.samples.navigation.components.Screen

@Composable
internal fun ArticleScreen(slug: String) {
    val navigator = LocalNavigator.current

    Screen(title = "Article: $slug") {
        Text("This is the article with slug \"$slug\".")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { navigator.navigate(Route.ShareSheet(slug)) }) {
            Text("Share")
        }
    }
}
