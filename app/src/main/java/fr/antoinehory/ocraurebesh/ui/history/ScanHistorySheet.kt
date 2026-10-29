package fr.antoinehory.ocraurebesh.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.domain.ScanRecord
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudBackground
import fr.antoinehory.ocraurebesh.ui.theme.HudCoral
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudSurfaceVariant
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary
import fr.antoinehory.ocraurebesh.ui.theme.HudTextSecondary
import java.text.DateFormat
import java.util.Date

/**
 * Volet modal (`ModalBottomSheet`) affichant l'historique local et les favoris sauvegardés dans Room,
 * avec boutons d'action compacts à icônes et conservation de l'état de défilement (`rememberLazyListState`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistorySheet(
    records: List<ScanRecord>,
    showFavoritesOnly: Boolean,
    onToggleFavoritesFilter: (Boolean) -> Unit,
    onToggleFavoriteItem: (Long, Boolean) -> Unit,
    onCopyText: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val dateFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HudBackground,
        contentColor = HudTextPrimary,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Historique",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary,
                )
                if (records.isNotEmpty() && !showFavoritesOnly) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            imageVector = HudIcons.Delete,
                            contentDescription = "Tout effacer",
                            tint = HudCoral,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !showFavoritesOnly,
                    onClick = { onToggleFavoritesFilter(false) },
                    label = { Text("Tous") },
                )
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = { onToggleFavoritesFilter(true) },
                    label = { Text("Favoris") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showFavoritesOnly) HudIcons.StarFilled else HudIcons.StarBorder,
                            contentDescription = null,
                            tint = if (showFavoritesOnly) HudAmber else HudTextSecondary,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                Text(
                    text = if (showFavoritesOnly) {
                        "Aucun favori enregistré."
                    } else {
                        "Aucune traduction enregistrée."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = records,
                        key = { it.id },
                        contentType = { "scan_record" },
                    ) { record ->
                        ScanHistoryItemCard(
                            record = record,
                            formattedDate = dateFormatter.format(Date(record.createdAt)),
                            onToggleFavorite = {
                                onToggleFavoriteItem(record.id, !record.isFavorite)
                            },
                            onCopy = { onCopyText(record.translatedText) },
                            onDelete = { onDeleteItem(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryItemCard(
    record: ScanRecord,
    formattedDate: String,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HudGlassBorder),
        colors = CardDefaults.cardColors(
            containerColor = HudSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = record.translatedText,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = HudIcons.Copy,
                        contentDescription = "Copier",
                        tint = HudCyan,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (record.isFavorite) HudIcons.StarFilled else HudIcons.StarBorder,
                        contentDescription = "Favori",
                        tint = if (record.isFavorite) HudAmber else HudTextSecondary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HudIcons.Delete,
                        contentDescription = "Supprimer",
                        tint = HudTextSecondary,
                    )
                }
            }
        }
    }
}
