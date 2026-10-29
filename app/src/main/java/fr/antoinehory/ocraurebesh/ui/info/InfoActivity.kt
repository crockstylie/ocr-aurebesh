package fr.antoinehory.ocraurebesh.ui.info

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme

/**
 * Activité affichant les informations personnelles du développeur, les réseaux sociaux
 * et le lien / bouton de donation PayPal.
 */
class InfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OCRAurebeshTheme {
                InfoScreen(
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, InfoActivity::class.java)
        }
    }
}
