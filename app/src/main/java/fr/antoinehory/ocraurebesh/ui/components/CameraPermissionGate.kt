package fr.antoinehory.ocraurebesh.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.antoinehory.ocraurebesh.R
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme

/**
 * Composant garde-barrière qui vérifie l'état de la permission `android.permission.CAMERA`
 * avec Accompanist Permissions.
 *
 * - Si la permission est accordée : affiche [content].
 * - Sinon : affiche un écran explicatif gérant les 3 états (demande initiale, rationale,
 *   et refus permanent avec redirection vers les paramètres système de l'application).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onPermissionResult = { hasRequestedPermission = true },
    )

    if (cameraPermissionState.status.isGranted) {
        content()
    } else {
        val isPermanentlyDenied =
            hasRequestedPermission && !cameraPermissionState.status.shouldShowRationale

        CameraPermissionRequestContent(
            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            isPermanentlyDenied = isPermanentlyDenied,
            onRequestPermission = {
                hasRequestedPermission = true
                cameraPermissionState.launchPermissionRequest()
            },
            onOpenAppSettings = { openAppSettings(context) },
            modifier = modifier,
        )
    }
}

/**
 * Contenu UI stateless de demande de permission caméra, découplé d'Accompanist pour les `@Preview`.
 */
@Composable
fun CameraPermissionRequestContent(
    shouldShowRationale: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val description = when {
                        isPermanentlyDenied ->
                            stringResource(R.string.camera_permission_permanently_denied)
                        shouldShowRationale ->
                            stringResource(R.string.camera_permission_rationale)
                        else ->
                            stringResource(R.string.camera_permission_initial)
                    }

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = if (isPermanentlyDenied) onOpenAppSettings else onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text(
                            text = if (isPermanentlyDenied) {
                                stringResource(R.string.camera_permission_open_settings)
                            } else {
                                stringResource(R.string.camera_permission_allow)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionInitialPreview() {
    OCRAurebeshTheme {
        CameraPermissionRequestContent(
            shouldShowRationale = false,
            isPermanentlyDenied = false,
            onRequestPermission = {},
            onOpenAppSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionPermanentDenialPreview() {
    OCRAurebeshTheme {
        CameraPermissionRequestContent(
            shouldShowRationale = false,
            isPermanentlyDenied = true,
            onRequestPermission = {},
            onOpenAppSettings = {},
        )
    }
}
