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
import android.graphics.Path
import android.graphics.PathIterator
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import androidx.annotation.RequiresApi
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
import kotlin.math.hypot
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
 * Utilitaire d'export haute définition de compositions Aurebesh aux formats SVG (tracés vectorisés `<path>`),
 * PNG et JPG dans le dossier public `Téléchargements/OCRAurebesh` via [MediaStore.Downloads], avec
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
                            val svgContent = buildVectorizedPathsSvg(
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
     * Génère un fichier SVG avec **tracés vectoriels (`<path d="..." />`)** ("Texte vectorisé" / "Create Outlines").
     *
     * Chaque glyphe Aurebesh est converti en courbes de Bézier SVG indépendantes via [Paint.getTextPath],
     * ce qui permet d'ouvrir et de manipuler chaque lettre directement dans Adobe Illustrator, Inkscape
     * ou Figma sans avoir besoin d'installer la moindre police sur l'ordinateur.
     */
    private fun buildVectorizedPathsSvg(
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
            this.textAlign = Paint.Align.LEFT
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * 1.25f
        val maxLineWidth = lines.maxOfOrNull { line ->
            if (line.isEmpty()) fontSizePx else textPaint.measureText(line)
        } ?: fontSizePx

        val svgWidth = max(320, (maxLineWidth + EXPORT_PADDING_PX * 2).roundToInt())
        val totalTextHeight = lineHeight * lines.size
        val svgHeight = max(180, (totalTextHeight + EXPORT_PADDING_PX * 2).roundToInt())

        val textHex = argbToHex(textColorArgb)
        val bgHex = argbToHex(backgroundColorArgb)

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<svg xmlns="http://www.w3.org/2000/svg" width="$svgWidth" height="$svgHeight" viewBox="0 0 $svgWidth $svgHeight">"""
            )

            if (!isBackgroundTransparent) {
                appendLine("""  <rect id="background" width="$svgWidth" height="$svgHeight" rx="24" fill="$bgHex" />""")
            }

            appendLine("""  <g id="aurebesh-outlines" fill="$textHex" fill-rule="nonzero">""")

            var currentBaselineY = EXPORT_PADDING_PX - fontMetrics.ascent
            val glyphPath = Path()

            lines.forEachIndexed { lineIndex, line ->
                if (line.isNotEmpty()) {
                    val lineWidth = textPaint.measureText(line)
                    val lineStartX = when (alignment) {
                        AurebeshTextAlignment.LEFT -> EXPORT_PADDING_PX
                        AurebeshTextAlignment.CENTER -> (svgWidth - lineWidth) / 2f
                        AurebeshTextAlignment.RIGHT -> svgWidth - EXPORT_PADDING_PX - lineWidth
                    }

                    appendLine("""    <g id="line-${lineIndex + 1}">""")

                    for (charIndex in line.indices) {
                        val ch = line[charIndex]
                        if (!ch.isWhitespace()) {
                            val charX = lineStartX + textPaint.measureText(line, 0, charIndex)
                            glyphPath.reset()
                            textPaint.getTextPath(
                                line,
                                charIndex,
                                charIndex + 1,
                                charX,
                                currentBaselineY,
                                glyphPath,
                            )
                            val d = pathToSvgData(glyphPath)
                            if (d.isNotEmpty()) {
                                appendLine("""      <path d="$d" />""")
                            }
                        }
                    }

                    appendLine("    </g>")
                }
                currentBaselineY += lineHeight
            }

            appendLine("  </g>")
            appendLine("</svg>")
        }
    }

    /**
     * Convertit un [Path] Android natif en chaîne de commandes SVG `d="M ... L ... Q ... C ... Z"`.
     * - Sur Android 14+ (API 34+), utilise [Path.getPathIterator] pour extraire les courbes de Bézier exactes.
     * - Sur Android 10–13 (API 29–33), utilise [Path.approximate] avec une tolérance fine (`0.15f`).
     */
    private fun pathToSvgData(path: Path): String {
        if (path.isEmpty) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pathToSvgDataApi34(path)
        } else {
            pathToSvgDataApproximate(path)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun pathToSvgDataApi34(path: Path): String {
        val sb = StringBuilder()
        val iterator = path.pathIterator
        val pts = FloatArray(8)

        while (iterator.hasNext()) {
            when (iterator.next(pts, 0)) {
                PathIterator.VERB_MOVE -> {
                    appendCmd(sb, "M ${fmt(pts[0])} ${fmt(pts[1])}")
                }

                PathIterator.VERB_LINE -> {
                    appendCmd(sb, "L ${fmt(pts[2])} ${fmt(pts[3])}")
                }

                PathIterator.VERB_QUAD -> {
                    appendCmd(
                        sb,
                        "Q ${fmt(pts[2])} ${fmt(pts[3])} ${fmt(pts[4])} ${fmt(pts[5])}",
                    )
                }

                PathIterator.VERB_CONIC -> {
                    // Conversion d'une conique rationnelle en courbe quadratique SVG
                    appendCmd(
                        sb,
                        "Q ${fmt(pts[2])} ${fmt(pts[3])} ${fmt(pts[4])} ${fmt(pts[5])}",
                    )
                }

                PathIterator.VERB_CUBIC -> {
                    appendCmd(
                        sb,
                        "C ${fmt(pts[2])} ${fmt(pts[3])} ${fmt(pts[4])} ${fmt(pts[5])} ${fmt(pts[6])} ${fmt(pts[7])}",
                    )
                }

                PathIterator.VERB_CLOSE -> {
                    appendCmd(sb, "Z")
                }

                PathIterator.VERB_DONE -> break
            }
        }
        return sb.toString()
    }

    private fun pathToSvgDataApproximate(path: Path): String {
        val approx = path.approximate(0.15f)
        if (approx.size < 6) return ""

        val sb = StringBuilder()
        var contourStartX = 0f
        var contourStartY = 0f
        var prevX = Float.NaN
        var prevY = Float.NaN
        var hasOpenContour = false

        var i = 0
        while (i <= approx.size - 3) {
            val fraction = approx[i]
            val x = approx[i + 1]
            val y = approx[i + 2]

            if (fraction == 0f && (i == 0 || approx[i - 3] > 0f)) {
                if (hasOpenContour) {
                    appendCmd(sb, "Z")
                }
                appendCmd(sb, "M ${fmt(x)} ${fmt(y)}")
                contourStartX = x
                contourStartY = y
                prevX = x
                prevY = y
                hasOpenContour = true
            } else if (hypot(x - prevX, y - prevY) > 0.05f) {
                appendCmd(sb, "L ${fmt(x)} ${fmt(y)}")
                prevX = x
                prevY = y
            }
            i += 3
        }

        if (hasOpenContour && hypot(prevX - contourStartX, prevY - contourStartY) < 2f) {
            appendCmd(sb, "Z")
        }

        return sb.toString()
    }

    private fun appendCmd(sb: StringBuilder, cmd: String) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(cmd)
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun argbToHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }
}
