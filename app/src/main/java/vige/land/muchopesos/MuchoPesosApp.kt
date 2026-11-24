package vige.land.muchopesos

import android.app.Application
import vige.land.muchopesos.ui.AppContainer

class MuchoPesosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
