package fr.antoinehory.ocraurebesh.ocr

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gestionnaire responsable de l'extraction du fichier modèle `aurebesh.traineddata`
 * depuis les `assets` de l'APK vers le stockage interne de l'application (`filesDir`),
 * afin que l'API native C++ de Tesseract puisse y accéder via un chemin système.
 */
class TessDataManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Prépare le répertoire `tesseract/tessdata/` et copie `aurebesh.traineddata` si nécessaire.
     *
     * @return [Result] contenant le chemin absolu du dossier parent `tesseract` à passer à `TessBaseAPI.init()`.
     */
    suspend fun ensureTessDataReady(): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val rootDir = File(context.filesDir, TESSERACT_DIR_NAME)
            val tessDataDir = File(rootDir, TESSDATA_SUBDIR_NAME)
            if (!tessDataDir.exists() && !tessDataDir.mkdirs()) {
                throw IllegalStateException("Impossible de créer le dossier tessdata : ${tessDataDir.absolutePath}")
            }

            val targetFile = File(tessDataDir, MODEL_FILENAME)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                copyAssetToFile(ASSET_PATH, targetFile)
            }

            rootDir.absolutePath
        }
    }

    private fun copyAssetToFile(assetPath: String, destFile: File) {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        if (destFile.exists()) {
            destFile.delete()
        }
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
    }

    companion object {
        const val LANGUAGE_CODE = "aurebesh"
        private const val TESSERACT_DIR_NAME = "tesseract"
        private const val TESSDATA_SUBDIR_NAME = "tessdata"
        private const val MODEL_FILENAME = "$LANGUAGE_CODE.traineddata"
        private const val ASSET_PATH = "$TESSDATA_SUBDIR_NAME/$MODEL_FILENAME"
    }
}
