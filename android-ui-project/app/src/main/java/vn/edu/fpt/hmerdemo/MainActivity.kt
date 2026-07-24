package vn.edu.fpt.hmerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.edu.fpt.hmerdemo.ui.HmerDemoApp
import vn.edu.fpt.hmerdemo.ui.theme.HMERDEMOTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HMERDEMOTheme(darkTheme = false, dynamicColor = false) {
                HmerDemoApp()
            }
        }
    }
}
