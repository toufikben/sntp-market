package dz.sntp.market

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dz.sntp.market.ui.MainScreen

/**
 * The notification permission is requested by [MainScreen], not here. Asking from
 * the activity on every onCreate bypassed the persisted "already asked" flag,
 * repeated a request Android answers silently, and discarded the answer so the
 * settings switch never learned that the user had refused.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen()
        }
    }
}
