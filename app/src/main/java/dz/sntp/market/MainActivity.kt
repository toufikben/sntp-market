package dz.sntp.market

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import dz.sntp.market.ui.MainScreen
import dz.sntp.market.ui.theme.SntpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SntpTheme {
                MainScreen()
            }
        }
    }
}
