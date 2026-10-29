package fr.antoinehory.ocraurebesh.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudCyanDim
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudSurfaceVariant
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary

/**
 * Hôte de notification HUD sombre et compact, destiné à être positionné en haut de l'écran
 * afin de ne jamais masquer les boutons d'action situés en bas.
 */
@Composable
fun HudTopSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { snackbarData ->
        HudSnackbarCard(snackbarData = snackbarData)
    }
}

@Composable
private fun HudSnackbarCard(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = HudSurfaceVariant.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, HudGlassBorder),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = HudTextPrimary,
                modifier = Modifier.weight(1f),
            )

            val actionLabel = snackbarData.visuals.actionLabel
            if (actionLabel != null) {
                Surface(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { snackbarData.performAction() },
                    shape = RoundedCornerShape(10.dp),
                    color = HudCyanDim,
                    border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.7f)),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = HudCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
