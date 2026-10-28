package bd.myfamily.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[AppVm::class.java]
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006A4E), secondary = Color(0xFFF42A41))) {
                Root(vm)
            }
        }
    }
}
