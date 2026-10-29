package fr.antoinehory.ocraurebesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.antoinehory.ocraurebesh.ui.scanner.ScannerRoute
import fr.antoinehory.ocraurebesh.ui.scanner.ScannerViewModel
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme

class MainActivity : ComponentActivity() {

    private val scannerViewModel: ScannerViewModel by viewModels {
        ScannerViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OCRAurebeshTheme {
                ScannerRoute(viewModel = scannerViewModel)
            }
        }
    }
}
