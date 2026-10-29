package fr.antoinehory.ocraurebesh.domain

import androidx.compose.runtime.Immutable

/**
 * Résultat d'une passe d'analyse OCR sur une frame caméra.
 */
@Immutable
sealed interface OcrResult {

    /**
     * Du texte Aurebesh a été reconnu avec succès.
     *
     * @property text Texte traduit en alphabet latin (nettoyé).
     * @property confidence Score de confiance moyen Tesseract (0 à 100).
     * @property wasInverted Indique si l'image a été inversée automatiquement (texte clair sur fond sombre).
     */
    @Immutable
    data class Success(
        val text: String,
        val confidence: Int,
        val wasInverted: Boolean
    ) : OcrResult

    /**
     * Aucun texte Aurebesh lisible ou suffisamment fiable n'a été détecté dans la zone de visée.
     */
    @Immutable
    data object Empty : OcrResult

    /**
     * Une erreur technique est survenue lors de l'initialisation ou de l'analyse OCR.
     */
    @Immutable
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : OcrResult
}
