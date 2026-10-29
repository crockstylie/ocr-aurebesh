package fr.antoinehory.ocraurebesh.ui.generator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme

/**
 * Activité dédiée à l'écriture en alphabet latin avec transcription et mise en page
 * temps réel en Aurebesh, et export aux formats SVG, PNG et JPG.
 */
class AurebeshGeneratorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialText = intent?.getStringExtra(EXTRA_INITIAL_TEXT).orEmpty()

        setContent {
            OCRAurebeshTheme {
                AurebeshGeneratorScreen(
                    initialText = initialText,
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_INITIAL_TEXT = "extra_initial_text"

        fun createIntent(context: Context, initialText: String = ""): Intent {
            return Intent(context, AurebeshGeneratorActivity::class.java).apply {
                if (initialText.isNotBlank()) {
                    putExtra(EXTRA_INITIAL_TEXT, initialText)
                }
            }
        }
    }
}
