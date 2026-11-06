package app.shomar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.shomar.ui.AppRoot
import app.shomar.ui.theme.ShomarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShomarApp).container
        setContent {
            ShomarTheme {
                AppRoot(container)
            }
        }
    }
}
