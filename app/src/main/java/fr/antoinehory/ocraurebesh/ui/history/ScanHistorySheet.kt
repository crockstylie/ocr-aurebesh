package fr.antoinehory.ocraurebesh.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.antoinehory.ocraurebesh.R
import fr.antoinehory.ocraurebesh.domain.ScanRecord
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudBackground
import fr.antoinehory.ocraurebesh.ui.theme.HudCoral
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassButton
import fr.antoinehory.ocraurebesh.ui.theme.HudSubtleBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudSurfaceVariant
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary
import fr.antoinehory.ocraurebesh.ui.theme.HudTextSecondary
import java.text.DateFormat
import java.util.Date

private sealed interface HistoryDeleteConfirmation {
    data object ClearAll : HistoryDeleteConfirmation
    data class SingleItem(val record: ScanRecord) : HistoryDeleteConfirmation
}

/**
 * Volet modal (`ModalBottomSheet`) affichant l'historique local et les favoris sauvegardés dans Room,
 * avec toast de confirmation avant suppression (unitaire ou totale) et bouton d'ouverture dans Studio Aurebesh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistorySheet(
    records: List<ScanRecord>,
    showFavoritesOnly: Boolean,
    onToggleFavoritesFilter: (Boolean) -> Unit,
    onToggleFavoriteItem: (Long, Boolean) -> Unit,
    onCopyText: (String) -> Unit,
    onEditInStudio: (String) -> Unit,
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
    var pendingConfirmation by remember { mutableStateOf<HistoryDeleteConfirmation?>(null) }

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
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary,
                )
                if (records.isNotEmpty() && !showFavoritesOnly) {
                    IconButton(
                        onClick = {
                            pendingConfirmation = HistoryDeleteConfirmation.ClearAll
                        }
                    ) {
                        Icon(
                            imageVector = HudIcons.Delete,
                            contentDescription = stringResource(R.string.history_clear_all),
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
                    label = { Text(stringResource(R.string.history_filter_all)) },
                )
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = { onToggleFavoritesFilter(true) },
                    label = { Text(stringResource(R.string.history_filter_favorites)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showFavoritesOnly) HudIcons.StarFilled else HudIcons.StarBorder,
                            contentDescription = null,
                            tint = if (showFavoritesOnly) HudAmber else HudTextSecondary,
                        )
                    },
                )
            }

            // Toast de contrôle / confirmation avant suppression d'une ligne ou de tout l'historique
            AnimatedVisibility(
                visible = pendingConfirmation != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val confirmation = pendingConfirmation
                if (confirmation != null) {
                    val promptText = when (confirmation) {
                        HistoryDeleteConfirmation.ClearAll ->
                            stringResource(R.string.history_confirm_clear_all)
                        is HistoryDeleteConfirmation.SingleItem ->
                            stringResource(
                                R.string.history_confirm_delete_item,
                                confirmation.record.translatedText,
                            )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = HudSurfaceVariant,
                        border = BorderStroke(1.dp, HudCoral.copy(alpha = 0.75f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = promptText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = HudTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Row(
                                modifier = Modifier.padding(start = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { pendingConfirmation = null },
                                    shape = RoundedCornerShape(10.dp),
                                    color = HudGlassButton,
                                    border = BorderStroke(1.dp, HudSubtleBorder),
                                ) {
                                    Text(
                                        text = stringResource(R.string.cancel),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HudTextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            when (confirmation) {
                                                HistoryDeleteConfirmation.ClearAll -> onClearAll()
                                                is HistoryDeleteConfirmation.SingleItem ->
                                                    onDeleteItem(confirmation.record.id)
                                            }
                                            pendingConfirmation = null
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = HudCoral.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, HudCoral),
                                ) {
                                    Text(
                                        text = stringResource(R.string.confirm_ok),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = HudCoral,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                Text(
                    text = if (showFavoritesOnly) {
                        stringResource(R.string.history_empty_favorites)
                    } else {
                        stringResource(R.string.history_empty_all)
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
                            onEditInStudio = { onEditInStudio(record.translatedText) },
                            onDelete = {
                                pendingConfirmation = HistoryDeleteConfirmation.SingleItem(record)
                            },
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
    onEditInStudio: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Texte sur toute la largeur de la carte (jamais tassé à gauche par les boutons)
            Text(
                text = record.translatedText,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = HudTextPrimary,
                modifier = Modifier.fillMaxWidth(),
            )

            // Pied de carte : date à gauche et boutons d'actions compacts à droite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactHistoryActionButton(
                        icon = HudIcons.Edit,
                        contentDescription = stringResource(R.string.cd_edit_in_studio),
                        tint = HudCyan,
                        onClick = onEditInStudio,
                    )
                    CompactHistoryActionButton(
                        icon = HudIcons.Copy,
                        contentDescription = stringResource(R.string.cd_copy),
                        tint = HudCyan,
                        onClick = onCopy,
                    )
                    CompactHistoryActionButton(
                        icon = if (record.isFavorite) HudIcons.StarFilled else HudIcons.StarBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (record.isFavorite) HudAmber else HudTextSecondary,
                        onClick = onToggleFavorite,
                    )
                    CompactHistoryActionButton(
                        icon = HudIcons.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = HudTextSecondary,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactHistoryActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(HudGlassButton, CircleShape)
            .border(1.dp, HudSubtleBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    }
}
