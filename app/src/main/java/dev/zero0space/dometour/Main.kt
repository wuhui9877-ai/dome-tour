package dev.zero0space.dometour

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import dev.zero0space.dometour.ui.DomeTourPage

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme {
                DomeTourPage(Modifier.windowConstraints(minWidth = 1280.dp, minHeight = 720.dp))
            }
        }
    }
