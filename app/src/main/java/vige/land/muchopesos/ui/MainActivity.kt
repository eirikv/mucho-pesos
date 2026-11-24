package vige.land.muchopesos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import vige.land.muchopesos.MuchoPesosApp
import vige.land.muchopesos.ui.navigation.AppNavHost
import vige.land.muchopesos.ui.theme.MuchoPesosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MuchoPesosApp).container

        setContent {
            MuchoPesosTheme {
                LaunchedEffect(Unit) {
                    container.ledgerRepository.initialize()
                }
                AppNavHost(container = container)
            }
        }
    }
}
