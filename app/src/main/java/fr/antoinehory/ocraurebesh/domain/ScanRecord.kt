package fr.antoinehory.ocraurebesh.domain

import androidx.compose.runtime.Immutable

/**
 * Modèle métier immuable représentant une traduction Aurebesh enregistrée dans l'historique.
 *
 * @property id Identifiant unique de l'enregistrement (0 avant insertion en base).
 * @property translatedText Texte traduit depuis l'alphabet Aurebesh vers l'alphabet latin.
 * @property confidence Score de confiance moyen de l'OCR (entre 0 et 100).
 * @property createdAt Horodatage de création en millisecondes (epoch).
 * @property isFavorite Indique si l'enregistrement est marqué comme favori par l'utilisateur.
 */
@Immutable
data class ScanRecord(
    val id: Long = 0L,
    val translatedText: String,
    val confidence: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
