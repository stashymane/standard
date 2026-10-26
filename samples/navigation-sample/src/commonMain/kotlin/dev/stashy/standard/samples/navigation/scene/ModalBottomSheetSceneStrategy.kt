package dev.stashy.standard.samples.navigation.scene

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Renders entries marked with [bottomSheet] in a Material3 [ModalBottomSheet] overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class ModalBottomSheetSceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        if (entry.metadata[BottomSheetKey] == null) return null

        return object : OverlayScene<NavKey> {
            override val key: Any = entry.contentKey
            override val entries: List<NavEntry<NavKey>> = listOf(entry)
            override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)
            override val overlaidEntries: List<NavEntry<NavKey>> = entries.dropLast(1)

            private lateinit var sheetState: SheetState

            override val content: @Composable () -> Unit = {
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
                ModalBottomSheet(
                    onDismissRequest = onBack,
                    sheetState = sheetState,
                ) {
                    entry.Content()
                }
            }

            override suspend fun onRemove() {
                if (::sheetState.isInitialized) {
                    sheetState.hide()
                }
            }
        }
    }

    companion object {
        private object BottomSheetKey : NavMetadataKey<Unit>

        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, Unit) }
    }
}
