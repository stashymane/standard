package dev.stashy.standard.samples.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.stashy.navigation.compose.*
import dev.stashy.navigation.ui.rememberNavigatorSceneDecorator

private val topLevelRoutes = setOf<NavKey>(Route.Home, Route.Search, Route.Profile("me"))

@Composable
fun App() {
    MaterialTheme(
        if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        val state = rememberNavigationState(
            startRoute = Route.Home,
            topLevelRoutes = topLevelRoutes,
        )
        val navigator = rememberNavigator<Route>(state)

        Scaffold(
            Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Navigation Sample") },
                    navigationIcon = {
                        if (navigator.canGoBack()) {
                            IconButton(onClick = { navigator.goBack() }) {
                                Text("←")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                BottomBar(
                    current = state.current,
                    onSelect = { navigator.navigate(it) },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                NavDisplay(
                    backStack = state.backStack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberEntryStateNavEntryDecorator(state),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    sceneDecoratorStrategies = listOf(
                        rememberNavigatorSceneDecorator(navigator),
                    ),
                    entryProvider = entryProvider {
                        entry<Route.Home> { HomeScreen() }
                        entry<Route.Search> { SearchScreen() }
                        entry<Route.SearchResults> { key -> SearchResultsScreen(key.query) }
                        entry<Route.Profile> { key -> ProfileScreen(key.id) }
                        entry<Route.Article> { key -> ArticleScreen(key.slug) }
                    },
                    onBack = { navigator.goBack() },
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    current: NavKey,
    onSelect: (NavKey) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Route.Home,
            onClick = { onSelect(Route.Home) },
            icon = { Text("H") },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = current == Route.Search,
            onClick = { onSelect(Route.Search) },
            icon = { Text("S") },
            label = { Text("Search") },
        )
        NavigationBarItem(
            selected = current is Route.Profile,
            onClick = { onSelect(Route.Profile("me")) },
            icon = { Text("P") },
            label = { Text("Profile") },
        )
    }
}

@Composable
private fun HomeScreen() {
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

@Composable
private fun SearchScreen() {
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

@Composable
private fun SearchResultsScreen(query: String) {
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

@Composable
private fun ProfileScreen(id: String) {
    val navigator = LocalNavigator.current

    Screen(title = "Profile: $id") {
        Text("Profile details for user $id.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { navigator.navigate(Route.Article("profile-$id")) }) {
            Text("View featured article")
        }
    }
}

@Composable
private fun ArticleScreen(slug: String) {
    Screen(title = "Article: $slug") {
        Text("This is the article with slug \"$slug\".")
    }
}

@Composable
private fun Screen(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        content()
    }
}
