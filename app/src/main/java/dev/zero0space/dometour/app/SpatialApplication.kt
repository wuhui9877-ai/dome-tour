package dev.zero0space.dometour.app

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import dev.zero0space.dometour.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
