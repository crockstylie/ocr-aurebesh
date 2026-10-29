package fr.antoinehory.ocraurebesh.ui.generator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.antoinehory.ocraurebesh.data.local.AppDatabase
import fr.antoinehory.ocraurebesh.data.repository.ScanHistoryRepository
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.components.HudTopSnackbarHost
import fr.antoinehory.ocraurebesh.ui.theme.HudAmber
import fr.antoinehory.ocraurebesh.ui.theme.HudAmberDim
import fr.antoinehory.ocraurebesh.ui.theme.HudBackground
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudCyanDim
import fr.antoinehory.ocraurebesh.ui.theme.HudEmerald
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassButton
import fr.antoinehory.ocraurebesh.ui.theme.HudGlassSurface
import fr.antoinehory.ocraurebesh.ui.theme.HudSubtleBorder
import fr.antoinehory.ocraurebesh.ui.theme.HudSurfaceVariant
import fr.antoinehory.ocraurebesh.ui.theme.HudTextPrimary
import fr.antoinehory.ocraurebesh.ui.theme.HudTextSecondary
import fr.antoinehory.ocraurebesh.ui.theme.OCRAurebeshTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

private enum class ColorPickerTarget {
    TEXT,
    BACKGROUND,
}

/**
 * Écran épuré du Studio / Générateur Aurebesh :
 * - Barre supérieure minimaliste sans titre : Retour à gauche, bouton Style/Couleurs et bouton Télécharger (sous-menu SVG/PNG/JPG) à droite.
 * - Espace maximal dédié à l'aperçu Aurebesh en direct et au champ de texte multi-lignes (sans label).
 * - Menu de style et couleurs activable/rétractable à la demande avec 9 polices Aurebesh, damier DAO pour la transparence et Color Picker complet.
 */
@Composable
fun AurebeshGeneratorScreen(
    initialText: String = "",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by rememberSaveable {
        mutableStateOf(initialText.ifBlank { "MAY THE FORCE\nBE WITH YOU" })
    }
    var selectedFontName by rememberSaveable {
        mutableStateOf(AurebeshFontOption.REGULAR.name)
    }
    var selectedAlignmentName by rememberSaveable {
        mutableStateOf(AurebeshTextAlignment.CENTER.name)
    }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(30f) }
    var textColorArgb by rememberSaveable {
        mutableIntStateOf(AurebeshColorPresets.textColors.first().argb)
    }
    var backgroundColorArgb by rememberSaveable {
        mutableIntStateOf(AurebeshColorPresets.backgroundColors.first().argb)
    }
    var isBackgroundTransparent by rememberSaveable { mutableStateOf(false) }
    var isStyleMenuVisible by rememberSaveable { mutableStateOf(false) }
    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }

    var isExporting by remember { mutableStateOf(false) }
    var lastExportedFile by remember { mutableStateOf<ExportedAurebeshFile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingFormatAfterPermission by remember { mutableStateOf<AurebeshExportFormat?>(null) }

    val selectedFont = remember(selectedFontName) {
        runCatching { AurebeshFontOption.valueOf(selectedFontName) }
            .getOrDefault(AurebeshFontOption.REGULAR)
    }
    val selectedAlignment = remember(selectedAlignmentName) {
        runCatching { AurebeshTextAlignment.valueOf(selectedAlignmentName) }
            .getOrDefault(AurebeshTextAlignment.CENTER)
    }

    val aurebeshFontFamily = remember(selectedFont, isPreview) {
        if (isPreview) {
            FontFamily.Monospace
        } else {
            runCatching {
                FontFamily(Typeface.createFromAsset(context.assets, selectedFont.assetPath))
            }.getOrDefault(FontFamily.Monospace)
        }
    }

    if (lastExportedFile != null) {
        val exported = lastExportedFile!!
        LaunchedEffect(exported) {
            snackbarHostState.showSnackbar(
                message = "Téléchargé : ${exported.fileName}",
            )
            lastExportedFile = null
        }
    }

    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            snackbarHostState.showSnackbar(errorMessage!!)
            errorMessage = null
        }
    }

    val runExport: (AurebeshExportFormat) -> Unit = { format ->
        if (!isExporting) {
            isExporting = true
            coroutineScope.launch {
                val result = AurebeshImageExporter.exportToDownloads(
                    context = context,
                    rawText = inputText,
                    fontOption = selectedFont,
                    alignment = selectedAlignment,
                    fontSizeSp = fontSizeSp,
                    textColorArgb = textColorArgb,
                    backgroundColorArgb = backgroundColorArgb,
                    isBackgroundTransparent = isBackgroundTransparent,
                    format = format,
                )
                isExporting = false
                result
                    .onSuccess { exportedFile ->
                        lastExportedFile = exportedFile
                    }
                    .onFailure { err ->
                        errorMessage = err.localizedMessage ?: "Erreur lors de l'export"
                    }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val format = pendingFormatAfterPermission
        pendingFormatAfterPermission = null
        if (format != null) {
            runExport(format)
        }
    }

    val triggerExport: (AurebeshExportFormat) -> Unit = { format ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFormatAfterPermission = format
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runExport(format)
        }
    }

    val saveToHistory: () -> Unit = {
        val textToSave = inputText.trim()
        if (textToSave.isBlank()) {
            errorMessage = "Écrivez un texte avant de sauvegarder"
        } else if (!isPreview) {
            coroutineScope.launch {
                runCatching {
                    val repo = ScanHistoryRepository(
                        AppDatabase.getInstance(context.applicationContext).scanHistoryDao()
                    )
                    repo.saveScan(
                        translatedText = textToSave,
                        confidence = 100,
                    )
                }.onSuccess {
                    snackbarHostState.showSnackbar("Texte enregistré dans l'historique")
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Erreur lors de la sauvegarde"
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = HudBackground,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GeneratorTopBar(
                    isStyleMenuVisible = isStyleMenuVisible,
                    isExporting = isExporting,
                    onNavigateBack = onNavigateBack,
                    onToggleStyleMenu = { isStyleMenuVisible = !isStyleMenuVisible },
                    onSaveToHistory = saveToHistory,
                    onExport = triggerExport,
                )

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isLandscape = maxWidth > maxHeight

                if (isLandscape) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AurebeshLivePreviewCard(
                                text = inputText,
                                fontFamily = aurebeshFontFamily,
                                alignment = selectedAlignment,
                                fontSizeSp = fontSizeSp,
                                textColor = Color(textColorArgb),
                                backgroundColor = Color(backgroundColorArgb),
                                isBackgroundTransparent = isBackgroundTransparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )

                            LatinMultiLineEditor(
                                inputText = inputText,
                                onInputTextChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }

                        AnimatedVisibility(
                            visible = isStyleMenuVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            StyleAndColorsMenuPanel(
                                selectedFont = selectedFont,
                                onSelectFont = { selectedFontName = it.name },
                                selectedAlignment = selectedAlignment,
                                onSelectAlignment = { selectedAlignmentName = it.name },
                                fontSizeSp = fontSizeSp,
                                onFontSizeChange = { fontSizeSp = it },
                                textColorArgb = textColorArgb,
                                onSelectTextColor = { textColorArgb = it },
                                onOpenTextColorPicker = {
                                    activeColorPickerTarget = ColorPickerTarget.TEXT
                                },
                                backgroundColorArgb = backgroundColorArgb,
                                isBackgroundTransparent = isBackgroundTransparent,
                                onSelectBackground = { preset ->
                                    backgroundColorArgb = preset.argb
                                    isBackgroundTransparent = preset.isTransparent
                                },
                                onOpenBackgroundColorPicker = {
                                    activeColorPickerTarget = ColorPickerTarget.BACKGROUND
                                },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Aperçu Aurebesh grand format
                        AurebeshLivePreviewCard(
                            text = inputText,
                            fontFamily = aurebeshFontFamily,
                            alignment = selectedAlignment,
                            fontSizeSp = fontSizeSp,
                            textColor = Color(textColorArgb),
                            backgroundColor = Color(backgroundColorArgb),
                            isBackgroundTransparent = isBackgroundTransparent,
                            modifier = Modifier
                                .weight(if (isStyleMenuVisible) 0.95f else 1.15f)
                                .fillMaxWidth(),
                        )

                        // Zone de saisie multi-lignes (sans label, occupe tout le reste quand le menu style est fermé)
                        LatinMultiLineEditor(
                            inputText = inputText,
                            onInputTextChange = { inputText = it },
                            modifier = Modifier
                                .weight(if (isStyleMenuVisible) 0.65f else 1f)
                                .fillMaxWidth(),
                        )

                        // Menu Style & Couleurs activable par le bouton de la barre du haut
                        AnimatedVisibility(
                            visible = isStyleMenuVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            StyleAndColorsMenuPanel(
                                selectedFont = selectedFont,
                                onSelectFont = { selectedFontName = it.name },
                                selectedAlignment = selectedAlignment,
                                onSelectAlignment = { selectedAlignmentName = it.name },
                                fontSizeSp = fontSizeSp,
                                onFontSizeChange = { fontSizeSp = it },
                                textColorArgb = textColorArgb,
                                onSelectTextColor = { textColorArgb = it },
                                onOpenTextColorPicker = {
                                    activeColorPickerTarget = ColorPickerTarget.TEXT
                                },
                                backgroundColorArgb = backgroundColorArgb,
                                isBackgroundTransparent = isBackgroundTransparent,
                                onSelectBackground = { preset ->
                                    backgroundColorArgb = preset.argb
                                    isBackgroundTransparent = preset.isTransparent
                                },
                                onOpenBackgroundColorPicker = {
                                    activeColorPickerTarget = ColorPickerTarget.BACKGROUND
                                },
                            )
                        }
                    }
                }
            }
            }

            // Toast HUD sombre positionné en haut (sous la barre d'outils) pour ne jamais gêner le bas
            HudTopSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp),
            )
        }

        if (activeColorPickerTarget != null) {
            val target = activeColorPickerTarget!!
            val initialArgb = when (target) {
                ColorPickerTarget.TEXT -> textColorArgb
                ColorPickerTarget.BACKGROUND ->
                    if (isBackgroundTransparent) 0xFF070B10.toInt() else backgroundColorArgb
            }
            HsvColorPickerDialog(
                title = when (target) {
                    ColorPickerTarget.TEXT -> "Couleur des lettres"
                    ColorPickerTarget.BACKGROUND -> "Couleur du fond"
                },
                initialColorArgb = initialArgb,
                onDismiss = { activeColorPickerTarget = null },
                onColorConfirmed = { chosenArgb ->
                    when (target) {
                        ColorPickerTarget.TEXT -> textColorArgb = chosenArgb
                        ColorPickerTarget.BACKGROUND -> {
                            backgroundColorArgb = chosenArgb
                            isBackgroundTransparent = false
                        }
                    }
                    activeColorPickerTarget = null
                },
            )
        }
    }
}

/**
 * Barre supérieure minimaliste sans titre :
 * - À gauche : bouton Retour.
 * - À droite : bouton d'ouverture/fermeture du menu Style & Couleurs + bouton unique Télécharger (avec sous-menu SVG, PNG, JPG).
 */
@Composable
private fun GeneratorTopBar(
    isStyleMenuVisible: Boolean,
    isExporting: Boolean,
    onNavigateBack: () -> Unit,
    onToggleStyleMenu: () -> Unit,
    onSaveToHistory: () -> Unit,
    onExport: (AurebeshExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDownloadMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
                contentDescription = "Retour au scanner",
                tint = HudTextPrimary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bouton d'activation du menu de style et couleurs
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isStyleMenuVisible) HudAmberDim else HudGlassButton,
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isStyleMenuVisible) HudAmber else HudSubtleBorder,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggleStyleMenu)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Menu de style et couleurs"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HudIcons.Tune,
                    contentDescription = null,
                    tint = if (isStyleMenuVisible) HudAmber else HudTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Bouton Sauvegarder dans l'historique (même table que les résultats de scan)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(HudEmerald.copy(alpha = 0.16f), CircleShape)
                    .border(1.dp, HudEmerald.copy(alpha = 0.65f), CircleShape)
                    .clickable(onClick = onSaveToHistory)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Sauvegarder dans l'historique"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HudIcons.BookmarkAdd,
                    contentDescription = null,
                    tint = HudEmerald,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Bouton unique Télécharger avec sous-menu déroulant (SVG, PNG, JPG)
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(HudCyanDim, CircleShape)
                        .border(1.dp, HudCyan.copy(alpha = 0.7f), CircleShape)
                        .clickable(enabled = !isExporting) {
                            isDownloadMenuExpanded = true
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Télécharger l'image"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HudIcons.Download,
                        contentDescription = null,
                        tint = HudCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = isDownloadMenuExpanded,
                    onDismissRequest = { isDownloadMenuExpanded = false },
                    containerColor = HudSurfaceVariant,
                ) {
                    AurebeshExportFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = format.label,
                                    fontWeight = FontWeight.Bold,
                                    color = HudTextPrimary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = HudIcons.Download,
                                    contentDescription = null,
                                    tint = HudCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                isDownloadMenuExpanded = false
                                onExport(format)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Champ de texte latin multi-lignes sans label flottant encombrant.
 */
@Composable
private fun LatinMultiLineEditor(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = inputText,
        onValueChange = onInputTextChange,
        placeholder = {
            Text(
                text = "Écrivez votre texte ici...",
                color = HudTextSecondary,
            )
        },
        trailingIcon = {
            if (inputText.isNotEmpty()) {
                IconButton(onClick = { onInputTextChange("") }) {
                    Icon(
                        imageVector = HudIcons.Close,
                        contentDescription = "Effacer le texte",
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    )
}

/**
 * Carte d'aperçu Aurebesh en direct. Si le fond transparent est sélectionné,
 * dessine le damier blanc et gris des outils de DAO en arrière-plan.
 */
@Composable
private fun AurebeshLivePreviewCard(
    text: String,
    fontFamily: FontFamily,
    alignment: AurebeshTextAlignment,
    fontSizeSp: Float,
    textColor: Color,
    backgroundColor: Color,
    isBackgroundTransparent: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val renderedText = text.ifBlank { "AUREBESH" }.lowercase(Locale.ROOT)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (isBackgroundTransparent) Color.Transparent else backgroundColor,
        border = BorderStroke(1.5.dp, HudGlassBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isBackgroundTransparent) {
                        Modifier.drawBehind {
                            drawCheckerboard(cellSizePx = 14.dp.toPx())
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(20.dp)
                .verticalScroll(scrollState),
            contentAlignment = when (alignment) {
                AurebeshTextAlignment.LEFT -> Alignment.CenterStart
                AurebeshTextAlignment.CENTER -> Alignment.Center
                AurebeshTextAlignment.RIGHT -> Alignment.CenterEnd
            },
        ) {
            Text(
                text = renderedText,
                fontFamily = fontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.3f).sp,
                color = textColor,
                textAlign = alignment.composeTextAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Panneau de style et couleurs (activable par bouton) :
 * - Choix parmi les 9 polices Aurebesh (sans le mot "Police").
 * - Alignement (Gauche, Centre, Droite) + Curseur de taille (sans texte de taille à côté).
 * - Couleurs des lettres et du fond avec pastille damier DAO pour la transparence et bouton Color Picker (+).
 */
@Composable
private fun StyleAndColorsMenuPanel(
    selectedFont: AurebeshFontOption,
    onSelectFont: (AurebeshFontOption) -> Unit,
    selectedAlignment: AurebeshTextAlignment,
    onSelectAlignment: (AurebeshTextAlignment) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    textColorArgb: Int,
    onSelectTextColor: (Int) -> Unit,
    onOpenTextColorPicker: () -> Unit,
    backgroundColorArgb: Int,
    isBackgroundTransparent: Boolean,
    onSelectBackground: (ColorPreset) -> Unit,
    onOpenBackgroundColorPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = HudGlassSurface,
        border = BorderStroke(1.dp, HudSubtleBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Sélection de la police Aurebesh (sans texte "Police")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AurebeshFontOption.entries.forEach { option ->
                    FilterChip(
                        selected = selectedFont == option,
                        onClick = { onSelectFont(option) },
                        label = { Text(option.displayName) },
                    )
                }
            }

            // 2. Alignement + Curseur de taille (sans indicateur numérique de taille)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AlignmentIconButton(
                        alignment = AurebeshTextAlignment.LEFT,
                        selected = selectedAlignment == AurebeshTextAlignment.LEFT,
                        onClick = { onSelectAlignment(AurebeshTextAlignment.LEFT) },
                    )
                    AlignmentIconButton(
                        alignment = AurebeshTextAlignment.CENTER,
                        selected = selectedAlignment == AurebeshTextAlignment.CENTER,
                        onClick = { onSelectAlignment(AurebeshTextAlignment.CENTER) },
                    )
                    AlignmentIconButton(
                        alignment = AurebeshTextAlignment.RIGHT,
                        selected = selectedAlignment == AurebeshTextAlignment.RIGHT,
                        onClick = { onSelectAlignment(AurebeshTextAlignment.RIGHT) },
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Slider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 16f..64f,
                    modifier = Modifier.weight(1f),
                )
            }

            // 3. Couleur des lettres + Color Picker
            Text(
                text = "Lettres",
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary,
            )
            ColorSwatchRow(
                presets = AurebeshColorPresets.textColors,
                selectedArgb = textColorArgb,
                isTransparentSelected = false,
                onSelect = { onSelectTextColor(it.argb) },
                onOpenCustomColorPicker = onOpenTextColorPicker,
            )

            // 4. Couleur du fond (avec damier DAO pour Transparent) + Color Picker
            Text(
                text = "Fond",
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary,
            )
            ColorSwatchRow(
                presets = AurebeshColorPresets.backgroundColors,
                selectedArgb = backgroundColorArgb,
                isTransparentSelected = isBackgroundTransparent,
                onSelect = onSelectBackground,
                onOpenCustomColorPicker = onOpenBackgroundColorPicker,
            )
        }
    }
}

@Composable
private fun AlignmentIconButton(
    alignment: AurebeshTextAlignment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when (alignment) {
        AurebeshTextAlignment.LEFT -> HudIcons.AlignLeft
        AurebeshTextAlignment.CENTER -> HudIcons.AlignCenter
        AurebeshTextAlignment.RIGHT -> HudIcons.AlignRight
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) HudCyanDim else HudGlassButton, CircleShape)
            .border(1.dp, if (selected) HudCyan else HudSubtleBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = alignment.label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) HudCyan else HudTextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Ligne de pastilles de couleur incluant :
 * - Les couleurs prédéfinies,
 * - Le damier blanc et gris DAO pour l'option Transparent,
 * - Un bouton roue chromatique "+" ouvrant le Color Picker.
 */
@Composable
private fun ColorSwatchRow(
    presets: List<ColorPreset>,
    selectedArgb: Int,
    isTransparentSelected: Boolean,
    onSelect: (ColorPreset) -> Unit,
    onOpenCustomColorPicker: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val isCustomColorSelected = !isTransparentSelected &&
        presets.none { !it.isTransparent && it.argb == selectedArgb }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            val isSelected = if (preset.isTransparent) {
                isTransparentSelected
            } else {
                !isTransparentSelected && selectedArgb == preset.argb
            }

            if (preset.isTransparent) {
                // Pastille damier blanc et gris type logiciel de DAO
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            drawCheckerboard(cellSizePx = 6.dp.toPx())
                        }
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) HudCyan else Color.White.copy(alpha = 0.45f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(preset) }
                        .semantics {
                            contentDescription = "Fond transparent"
                            role = Role.Button
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(preset.argb), CircleShape)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) HudCyan else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(preset) }
                        .semantics {
                            contentDescription = preset.name
                            role = Role.Button
                        }
                )
            }
        }

        // Bouton Color Picker (roue chromatique multicolore)
        val rainbowBrush = remember {
            Brush.sweepGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                )
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(brush = rainbowBrush, shape = CircleShape)
                .border(
                    width = if (isCustomColorSelected) 2.5.dp else 1.dp,
                    color = if (isCustomColorSelected) HudCyan else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .clickable(onClick = onOpenCustomColorPicker)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Choisir une couleur personnalisée"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        color = if (isCustomColorSelected) Color(selectedArgb) else HudSurfaceVariant.copy(alpha = 0.78f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HudIcons.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Dessine un damier blanc et gris clair standard des outils de DAO (Photoshop / Illustrator / GIMP).
 */
private fun DrawScope.drawCheckerboard(cellSizePx: Float) {
    val lightSquare = Color(0xFFFFFFFF)
    val graySquare = Color(0xFFCCCCCC)
    val cols = ceil(size.width / cellSizePx).toInt()
    val rows = ceil(size.height / cellSizePx).toInt()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val isLight = (row + col) % 2 == 0
            drawRect(
                color = if (isLight) lightSquare else graySquare,
                topLeft = Offset(col * cellSizePx, row * cellSizePx),
                size = Size(cellSizePx, cellSizePx),
            )
        }
    }
}

/**
 * Boîte de dialogue Color Picker HSV (pavé 2D Saturation/Valeur + barre de Teinte + saisie Hexadécimale).
 */
@Composable
private fun HsvColorPickerDialog(
    title: String,
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onColorConfirmed: (Int) -> Unit,
) {
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColorArgb = remember(hue, saturation, value) {
        AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    var hexInput by remember(initialColorArgb) {
        mutableStateOf(String.format(Locale.US, "#%06X", 0xFFFFFF and initialColorArgb))
    }

    val syncHexFromHsv: (Float, Float, Float) -> Unit = { h, s, v ->
        val argb = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        hexInput = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurfaceVariant,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HudTextPrimary,
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(currentColorArgb), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Pavé 2D Saturation (X) / Luminosité-Valeur (Y)
                val pureHueColor = remember(hue) {
                    Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, HudSubtleBorder, RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newSat = (offset.x / size.width).coerceIn(0f, 1f)
                                val newVal = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                saturation = newSat
                                value = newVal
                                syncHexFromHsv(hue, newSat, newVal)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newSat = (change.position.x / size.width).coerceIn(0f, 1f)
                                val newVal = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                saturation = newSat
                                value = newVal
                                syncHexFromHsv(hue, newSat, newVal)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, pureHueColor)
                            )
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black)
                            )
                        )
                        val cx = saturation * size.width
                        val cy = (1f - value) * size.height
                        drawCircle(
                            color = Color.Black,
                            radius = 9.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // 2. Barre de Teinte (Hue 0°..360°)
                val hueColors = remember {
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, HudSubtleBorder, RoundedCornerShape(50))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newHue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                                hue = newHue
                                syncHexFromHsv(newHue, saturation, value)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newHue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                                hue = newHue
                                syncHexFromHsv(newHue, saturation, value)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(brush = Brush.horizontalGradient(hueColors))
                        val hx = (hue / 360f) * size.width
                        drawCircle(
                            color = Color.White,
                            radius = (size.height / 2f) - 2.dp.toPx(),
                            center = Offset(hx, size.height / 2f),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }

                // 3. Champ de saisie du code Hexadécimal (#RRGGBB)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw
                            .uppercase(Locale.US)
                            .filterIndexed { index, ch ->
                                (index == 0 && ch == '#') || ch in '0'..'9' || ch in 'A'..'F'
                            }
                        val maxLen = if (cleaned.startsWith("#")) 7 else 6
                        val bounded = cleaned.take(maxLen)
                        hexInput = bounded

                        val hexDigits = bounded.removePrefix("#")
                        if (hexDigits.length == 6) {
                            val rgb = hexDigits.toIntOrNull(16)
                            if (rgb != null) {
                                val parsedArgb = (0xFF shl 24) or rgb
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(parsedArgb, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("#00E5FF") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorConfirmed(currentColorArgb) }) {
                Text("Valider", color = HudCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = HudTextSecondary)
            }
        },
    )
}

@Preview(name = "Studio Portrait", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun AurebeshGeneratorScreenPreview() {
    OCRAurebeshTheme {
        AurebeshGeneratorScreen(
            initialText = "MAY THE FORCE\nBE WITH YOU",
            onNavigateBack = {},
        )
    }
}

@Preview(name = "Studio Landscape", showBackground = true, widthDp = 892, heightDp = 412)
@Composable
private fun AurebeshGeneratorScreenLandscapePreview() {
    OCRAurebeshTheme {
        AurebeshGeneratorScreen(
            initialText = "MAY THE FORCE\nBE WITH YOU",
            onNavigateBack = {},
        )
    }
}
