package fr.antoinehory.ocraurebesh.ui.generator

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Métadonnées d'un fichier Aurebesh exporté dans `Téléchargements/OCRAurebesh`.
 */
@Immutable
data class ExportedAurebeshFile(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val format: AurebeshExportFormat,
)

/**
 * Utilitaire d'export haute définition de compositions Aurebesh aux formats SVG, PNG et JPG
 * dans le dossier public `Téléchargements/OCRAurebesh` via [MediaStore.Downloads], avec
 * notification système permettant d'ouvrir ou de partager directement le fichier généré.
 */
object AurebeshImageExporter {

    private const val EXPORT_PADDING_PX = 72f
    private const val EXPORT_SCALE_FACTOR = 3.2f
    private const val DEFAULT_DARK_BG_FOR_JPG = 0xFF070B10.toInt()
    private const val NOTIFICATION_CHANNEL_ID = "aurebesh_exports_channel"

    /**
     * Génère et enregistre le fichier dans `Téléchargements/OCRAurebesh`, puis publie
     * une notification système avec les actions **Partager** et **Ouvrir**.
     *
     * @return [Result] contenant les métadonnées [ExportedAurebeshFile] du fichier créé.
     */
    suspend fun exportToDownloads(
        context: Context,
        rawText: String,
        fontOption: AurebeshFontOption,
        alignment: AurebeshTextAlignment,
        fontSizeSp: Float,
        textColorArgb: Int,
        backgroundColorArgb: Int,
        isBackgroundTransparent: Boolean,
        format: AurebeshExportFormat,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<ExportedAurebeshFile> = withContext(ioDispatcher) {
        runCatching {
            val normalizedText = rawText.ifBlank { "AUREBESH" }.lowercase(Locale.ROOT)
            val lines = normalizedText.split("\n")
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "aurebesh_$timestamp.${format.extension}"

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/OCRAurebesh",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Impossible de créer le fichier dans Téléchargements")

            var previewBitmapForNotification: Bitmap? = null

            try {
                resolver.openOutputStream(uri)?.use { outStream ->
                    when (format) {
                        AurebeshExportFormat.SVG -> {
                            val svgContent = buildSelfContainedSvg(
                                context = context,
                                lines = lines,
                                fontOption = fontOption,
                                alignment = alignment,
                                fontSizePx = fontSizeSp * EXPORT_SCALE_FACTOR,
                                textColorArgb = textColorArgb,
                                backgroundColorArgb = backgroundColorArgb,
                                isBackgroundTransparent = isBackgroundTransparent,
                            )
                            outStream.write(svgContent.toByteArray(Charsets.UTF_8))
                        }

                        AurebeshExportFormat.PNG -> {
                            previewBitmapForNotification = writeRasterImage(
                                context = context,
                                lines = lines,
                                fontOption = fontOption,
                                alignment = alignment,
                                fontSizePx = fontSizeSp * EXPORT_SCALE_FACTOR,
                                textColorArgb = textColorArgb,
                                backgroundColorArgb = backgroundColorArgb,
                                isBackgroundTransparent = isBackgroundTransparent,
                                compressFormat = Bitmap.CompressFormat.PNG,
                                quality = 100,
                                outputStream = outStream,
                            )
                        }

                        AurebeshExportFormat.JPG -> {
                            val effectiveBg = if (isBackgroundTransparent) {
                                DEFAULT_DARK_BG_FOR_JPG
                            } else {
                                backgroundColorArgb
                            }
                            previewBitmapForNotification = writeRasterImage(
                                context = context,
                                lines = lines,
                                fontOption = fontOption,
                                alignment = alignment,
                                fontSizePx = fontSizeSp * EXPORT_SCALE_FACTOR,
                                textColorArgb = textColorArgb,
                                backgroundColorArgb = effectiveBg,
                                isBackgroundTransparent = false,
                                compressFormat = Bitmap.CompressFormat.JPEG,
                                quality = 95,
                                outputStream = outStream,
                            )
                        }
                    }
                    outStream.flush()
                } ?: throw IllegalStateException("Flux d'écriture indisponible")

                val completedValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, completedValues, null, null)

                val exportedFile = ExportedAurebeshFile(
                    uri = uri,
                    fileName = fileName,
                    mimeType = format.mimeType,
                    format = format,
                )

                postDownloadNotification(
                    context = context,
                    exportedFile = exportedFile,
                    previewBitmap = previewBitmapForNotification,
                )

                exportedFile
            } catch (e: Exception) {
                previewBitmapForNotification?.recycle()
                resolver.delete(uri, null, null)
                throw e
            }
        }
    }

    /**
     * Ouvre directement la feuille de partage système Android pour un fichier exporté.
     */
    fun shareExportedFile(context: Context, exportedFile: ExportedAurebeshFile) {
        val shareIntent = createShareChooserIntent(exportedFile).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    private fun postDownloadNotification(
        context: Context,
        exportedFile: ExportedAurebeshFile,
        previewBitmap: Bitmap?,
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            ensureNotificationChannel(context)

            val notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Intent pour ouvrir le fichier au clic sur la notification
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(exportedFile.uri, exportedFile.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                Intent.createChooser(openIntent, "Ouvrir ${exportedFile.fileName}"),
                pendingFlags,
            )

            // Intent pour l'action "Partager" dans la notification
            val shareChooserIntent = createShareChooserIntent(exportedFile)
            val sharePendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                shareChooserIntent,
                pendingFlags,
            )

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Image Aurebesh téléchargée (${exportedFile.format.label})")
                .setContentText("${exportedFile.fileName} • Téléchargements/OCRAurebesh")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_share,
                    "Partager",
                    sharePendingIntent,
                )
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "Ouvrir",
                    openPendingIntent,
                )

            if (previewBitmap != null && !previewBitmap.isRecycled) {
                builder.setLargeIcon(previewBitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(previewBitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText("Appuyez pour ouvrir ou utilisez Partager")
                )
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } finally {
            previewBitmap?.recycle()
        }
    }

    private fun createShareChooserIntent(exportedFile: ExportedAurebeshFile): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = exportedFile.mimeType
            putExtra(Intent.EXTRA_STREAM, exportedFile.uri)
            clipData = ClipData.newRawUri(exportedFile.fileName, exportedFile.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Partager ${exportedFile.fileName}").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Téléchargements Aurebesh",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications de téléchargement et partage des créations Aurebesh"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Écrit l'image matricielle dans [outputStream] et retourne le [Bitmap] généré
     * afin de pouvoir l'afficher en aperçu dans la notification système avant recyclage.
     */
    private fun writeRasterImage(
        context: Context,
        lines: List<String>,
        fontOption: AurebeshFontOption,
        alignment: AurebeshTextAlignment,
        fontSizePx: Float,
        textColorArgb: Int,
        backgroundColorArgb: Int,
        isBackgroundTransparent: Boolean,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        outputStream: OutputStream,
    ): Bitmap {
        val typeface = Typeface.createFromAsset(context.assets, fontOption.assetPath)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = fontSizePx
            this.color = textColorArgb
            this.textAlign = when (alignment) {
                AurebeshTextAlignment.LEFT -> Paint.Align.LEFT
                AurebeshTextAlignment.CENTER -> Paint.Align.CENTER
                AurebeshTextAlignment.RIGHT -> Paint.Align.RIGHT
            }
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * 1.25f
        val maxLineWidth = lines.maxOfOrNull { line ->
            if (line.isEmpty()) fontSizePx else textPaint.measureText(line)
        } ?: fontSizePx

        val canvasWidth = max(320, (maxLineWidth + EXPORT_PADDING_PX * 2).roundToInt())
        val totalTextHeight = lineHeight * lines.size
        val canvasHeight = max(180, (totalTextHeight + EXPORT_PADDING_PX * 2).roundToInt())

        val bitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (!isBackgroundTransparent) {
            canvas.drawColor(backgroundColorArgb)
        }

        val anchorX = when (alignment) {
            AurebeshTextAlignment.LEFT -> EXPORT_PADDING_PX
            AurebeshTextAlignment.CENTER -> canvasWidth / 2f
            AurebeshTextAlignment.RIGHT -> canvasWidth - EXPORT_PADDING_PX
        }

        var currentBaselineY = EXPORT_PADDING_PX - fontMetrics.ascent
        for (line in lines) {
            canvas.drawText(line, anchorX, currentBaselineY, textPaint)
            currentBaselineY += lineHeight
        }

        bitmap.compress(compressFormat, quality, outputStream)
        return bitmap
    }

    /**
     * Génère un fichier SVG 100 % autonome en embarquant la police `.ttf` en Base64 (`@font-face`)
     * afin que les glyphes Aurebesh s'affichent fidèlement sur n'importe quel appareil ou logiciel vectoriel.
     */
    private fun buildSelfContainedSvg(
        context: Context,
        lines: List<String>,
        fontOption: AurebeshFontOption,
        alignment: AurebeshTextAlignment,
        fontSizePx: Float,
        textColorArgb: Int,
        backgroundColorArgb: Int,
        isBackgroundTransparent: Boolean,
    ): String {
        val typeface = Typeface.createFromAsset(context.assets, fontOption.assetPath)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = fontSizePx
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * 1.25f
        val maxLineWidth = lines.maxOfOrNull { line ->
            if (line.isEmpty()) fontSizePx else textPaint.measureText(line)
        } ?: fontSizePx

        val svgWidth = max(320, (maxLineWidth + EXPORT_PADDING_PX * 2).roundToInt())
        val totalTextHeight = lineHeight * lines.size
        val svgHeight = max(180, (totalTextHeight + EXPORT_PADDING_PX * 2).roundToInt())

        val fontBytes = context.assets.open(fontOption.assetPath).use { it.readBytes() }
        val fontBase64 = Base64.encodeToString(fontBytes, Base64.NO_WRAP)
        val isOtf = fontOption.assetPath.endsWith(".otf", ignoreCase = true)
        val fontMime = if (isOtf) "font/otf" else "font/ttf"
        val fontFormat = if (isOtf) "opentype" else "truetype"

        val textHex = argbToHex(textColorArgb)
        val bgHex = argbToHex(backgroundColorArgb)

        val (anchorAttr, anchorX) = when (alignment) {
            AurebeshTextAlignment.LEFT -> "start" to EXPORT_PADDING_PX.roundToInt()
            AurebeshTextAlignment.CENTER -> "middle" to (svgWidth / 2)
            AurebeshTextAlignment.RIGHT -> "end" to (svgWidth - EXPORT_PADDING_PX.roundToInt())
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$svgWidth" height="$svgHeight" viewBox="0 0 $svgWidth $svgHeight">""")
            appendLine("  <defs>")
            appendLine("    <style>")
            appendLine("      @font-face {")
            appendLine("        font-family: '${fontOption.name}';")
            appendLine("        src: url('data:$fontMime;base64,$fontBase64') format('$fontFormat');")
            appendLine("      }")
            appendLine("      .aur-text {")
            appendLine("        font-family: '${fontOption.name}', sans-serif;")
            appendLine("        font-size: ${fontSizePx.roundToInt()}px;")
            appendLine("        fill: $textHex;")
            appendLine("      }")
            appendLine("    </style>")
            appendLine("  </defs>")

            if (!isBackgroundTransparent) {
                appendLine("""  <rect width="100%" height="100%" rx="24" fill="$bgHex" />""")
            }

            var currentBaselineY = EXPORT_PADDING_PX - fontMetrics.ascent
            for (line in lines) {
                val escaped = escapeXml(line)
                appendLine(
                    """  <text x="$anchorX" y="${currentBaselineY.roundToInt()}" text-anchor="$anchorAttr" class="aur-text" xml:space="preserve">$escaped</text>"""
                )
                currentBaselineY += lineHeight
            }
            appendLine("</svg>")
        }
    }

    private fun argbToHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
