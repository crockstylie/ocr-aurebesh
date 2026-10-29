package fr.antoinehory.ocraurebesh.ui.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.R
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudAmberDim
import fr.antoinehory.ocraurebesh.ui.theme.HudBackground
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudCyanDim
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassButton
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassSurface
import fr.antoinehory.ocraurebesh.ui.theme.HudSubtleBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary
import fr.antoinehory.ocraurebesh.ui.theme.HudTextSecondary
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme

/**
 * Écran Informations personnelles, réseaux sociaux, bouton de notation 5 étoiles Play Store
 * et bouton de donation PayPal.
 */
@Composable
fun InfoScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = HudBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Barre supérieure avec bouton retour
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .background(HudGlassButton, CircleShape)
                        .border(1.dp, HudSubtleBorder, CircleShape),
                ) {
                    Icon(
                        imageVector = HudIcons.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = HudTextPrimary,
                    )
                }
            }

            Text(
                text = stringResource(R.string.personal_info_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = HudTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = HudGlassSurface,
                border = BorderStroke(1.dp, HudGlassBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    InfoItemColumn(
                        label = stringResource(R.string.developer_label),
                        value = "Antoine Crock HORY",
                    )
                    InfoItemColumn(
                        label = stringResource(R.string.website_label),
                        value = "antoinehory.fr",
                        isLink = true,
                        linkUri = "https://antoinehory.fr",
                    )
                    InfoItemColumn(
                        label = stringResource(R.string.email_label),
                        value = "contact@antoinehory.fr",
                        isLink = true,
                        linkUri = "mailto:contact@antoinehory.fr",
                    )
                    InfoItemColumn(
                        label = stringResource(R.string.donate_label),
                        value = "paypal.me/kuroku",
                        isLink = true,
                        linkUri = "https://paypal.me/kuroku",
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bouton de don PayPal
                    Button(
                        onClick = { uriHandler.openUri("https://paypal.me/kuroku") },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HudAmberDim,
                            contentColor = HudAmber,
                        ),
                        border = BorderStroke(1.dp, HudAmber),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    ) {
                        Text(
                            text = stringResource(R.string.donate_button),
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bouton pour noter l'application 5 étoiles sur le Google Play Store
                    Button(
                        onClick = { openPlayStoreListing(context) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HudCyanDim,
                            contentColor = HudCyan,
                        ),
                        border = BorderStroke(1.dp, HudCyan),
                        modifier = Modifier.fillMaxWidth(0.85f),
                    ) {
                        Text(
                            text = stringResource(R.string.rate_app_button),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.find_me_on),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HudTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = HudGlassSurface,
                border = BorderStroke(1.dp, HudSubtleBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_linkedin_logo,
                        contentDescription = "linkedin_profile",
                        url = "https://www.linkedin.com/in/antoinehory/",
                    )
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_behance_logo,
                        contentDescription = "behance_profile",
                        url = "https://www.behance.net/antoine-hory",
                    )
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_instagram_logo,
                        contentDescription = "instagram_profile",
                        url = "https://www.instagram.com/antoine.hory.web/",
                    )
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_facebook_logo,
                        contentDescription = "facebook_profile",
                        url = "https://www.facebook.com/antoinehory/",
                    )
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_spotify_logo,
                        contentDescription = "spotify_profile",
                        url = "https://open.spotify.com/user/crockstylie",
                    )
                    SocialMediaIcon(
                        iconResId = R.drawable.ic_steam_logo,
                        contentDescription = "steam_profile",
                        url = "https://steamcommunity.com/id/crockstylie/",
                    )
                }
            }
        }
    }
}

private fun openPlayStoreListing(context: Context) {
    val packageName = context.packageName
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}

@Composable
fun InfoItemColumn(
    label: String,
    value: String,
    isLink: Boolean = false,
    linkUri: String? = null,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = "$label :",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = HudTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (isLink && linkUri != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = HudCyan,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(linkUri) },
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = HudTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SocialMediaIcon(
    iconResId: Int,
    contentDescription: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    IconButton(
        onClick = { uriHandler.openUri(url) },
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoScreenPreview() {
    OCRAurebeshTheme {
        InfoScreen(onNavigateBack = {})
    }
}
