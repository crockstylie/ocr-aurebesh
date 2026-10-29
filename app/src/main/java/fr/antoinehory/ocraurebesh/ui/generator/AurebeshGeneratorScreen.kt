package fr.antoinehory.ocraurebesh.ui.generator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
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
import fr.antoinehory.ocraurebesh.ui.components.HudIcons
import fr.antoinehory.ocraurebesh.ui.theme.HudBackground
import fr.antoinehory.ocraurebesh.ui.theme.HudCyan
import fr.antoinehory.ocraurebesh.ui.theme.HudCyanDim
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
import kotlin.math.roundToInt

/**
 * Écran du Studio / Générateur Aurebesh permettant de saisir du texte en alphabet latin,
 * de personnaliser la police Aurebesh, l'alignement, les sauts de ligne, la taille,
 * la couleur de lettre et de fond, et de télécharger le rendu en SVG, PNG ou JPG.
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
    var isExporting by remember { mutableStateOf(false) }
    var lastExportedFile by remember { mutableStateOf<ExportedAurebeshFile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingFormatAfterPermission by remember { mutableStateOf<AurebeshExportFormat?>(null) }

    val selectedFont = remember(selectedFontName) {
        AurebeshFontOption.valueOf(selectedFontName)
    }
    val selectedAlignment = remember(selectedAlignmentName) {
        AurebeshTextAlignment.valueOf(selectedAlignmentName)
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
            val result = snackbarHostState.showSnackbar(
                message = "Téléchargé : ${exported.fileName}",
                actionLabel = "Partager",
            )
            if (result == SnackbarResult.ActionPerformed) {
                AurebeshImageExporter.shareExportedFile(context, exported)
            }
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = HudBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            GeneratorTopBar(
                isExporting = isExporting,
                onNavigateBack = onNavigateBack,
                onExport = triggerExport,
            )

            Spacer(modifier = Modifier.height(12.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isLandscape = maxWidth > maxHeight

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

                        GeneratorControlsColumn(
                            inputText = inputText,
                            onInputTextChange = { inputText = it },
                            selectedFont = selectedFont,
                            onSelectFont = { selectedFontName = it.name },
                            selectedAlignment = selectedAlignment,
                            onSelectAlignment = { selectedAlignmentName = it.name },
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = { fontSizeSp = it },
                            textColorArgb = textColorArgb,
                            onSelectTextColor = { textColorArgb = it },
                            backgroundColorArgb = backgroundColorArgb,
                            isBackgroundTransparent = isBackgroundTransparent,
                            onSelectBackground = { preset ->
                                backgroundColorArgb = preset.argb
                                isBackgroundTransparent = preset.isTransparent
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                                .fillMaxWidth()
                                .heightIn(min = 170.dp, max = 240.dp),
                        )

                        GeneratorControlsColumn(
                            inputText = inputText,
                            onInputTextChange = { inputText = it },
                            selectedFont = selectedFont,
                            onSelectFont = { selectedFontName = it.name },
                            selectedAlignment = selectedAlignment,
                            onSelectAlignment = { selectedAlignmentName = it.name },
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = { fontSizeSp = it },
                            textColorArgb = textColorArgb,
                            onSelectTextColor = { textColorArgb = it },
                            backgroundColorArgb = backgroundColorArgb,
                            isBackgroundTransparent = isBackgroundTransparent,
                            onSelectBackground = { preset ->
                                backgroundColorArgb = preset.argb
                                isBackgroundTransparent = preset.isTransparent
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorTopBar(
    isExporting: Boolean,
    onNavigateBack: () -> Unit,
    onExport: (AurebeshExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(HudGlassButton, CircleShape)
                    .border(1.dp, HudSubtleBorder, CircleShape),
            ) {
                Icon(
                    imageVector = HudIcons.ArrowBack,
                    contentDescription = "Retour au scanner",
                    tint = HudTextPrimary,
                )
            }

            Text(
                text = "Studio Aurebesh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HudTextPrimary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AurebeshExportFormat.entries.forEach { format ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isExporting) { onExport(format) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Télécharger ${format.label}"
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = HudCyanDim,
                    border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = HudIcons.Download,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = HudCyan,
                        )
                    }
                }
            }
        }
    }
}

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
    val effectiveBg = if (isBackgroundTransparent) HudSurfaceVariant else backgroundColor

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = effectiveBg,
        border = BorderStroke(1.5.dp, HudGlassBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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

@Composable
private fun GeneratorControlsColumn(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    selectedFont: AurebeshFontOption,
    onSelectFont: (AurebeshFontOption) -> Unit,
    selectedAlignment: AurebeshTextAlignment,
    onSelectAlignment: (AurebeshTextAlignment) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    textColorArgb: Int,
    onSelectTextColor: (Int) -> Unit,
    backgroundColorArgb: Int,
    isBackgroundTransparent: Boolean,
    onSelectBackground: (ColorPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Saisie du texte latin (multi-lignes)
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            label = { Text("Texte latin (sauts de ligne supportés)") },
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
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // 2. Choix de la police Aurebesh & Alignement
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HudGlassSurface,
            border = BorderStroke(1.dp, HudSubtleBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Choix de la police Aurebesh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Police",
                        style = MaterialTheme.typography.labelMedium,
                        color = HudTextSecondary,
                        modifier = Modifier.width(52.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AurebeshFontOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedFont == option,
                                onClick = { onSelectFont(option) },
                                label = { Text(option.displayName) },
                            )
                        }
                    }
                }

                // Alignement et Taille du texte
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

                    Text(
                        text = "${fontSizeSp.roundToInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = HudTextSecondary,
                        modifier = Modifier.width(42.dp),
                    )
                    Slider(
                        value = fontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 16f..64f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. Couleur de lettre & Couleur de fond
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HudGlassSurface,
            border = BorderStroke(1.dp, HudSubtleBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Couleur des lettres",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextSecondary,
                )
                ColorSwatchRow(
                    presets = AurebeshColorPresets.textColors,
                    selectedArgb = textColorArgb,
                    isTransparentSelected = false,
                    onSelect = { onSelectTextColor(it.argb) },
                )

                Text(
                    text = "Couleur du fond",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextSecondary,
                )
                ColorSwatchRow(
                    presets = AurebeshColorPresets.backgroundColors,
                    selectedArgb = backgroundColorArgb,
                    isTransparentSelected = isBackgroundTransparent,
                    onSelect = onSelectBackground,
                )
            }
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

@Composable
private fun ColorSwatchRow(
    presets: List<ColorPreset>,
    selectedArgb: Int,
    isTransparentSelected: Boolean,
    onSelect: (ColorPreset) -> Unit,
) {
    val scrollState = rememberScrollState()
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
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(preset) },
                    label = { Text("Transp.") },
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
    }
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
