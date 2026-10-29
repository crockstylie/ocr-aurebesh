# ROADMAP.md — Suivi d'Avancement du Projet

> Ce fichier trace l'état exact du développement d'**OCRAurebesh**. L'agent IA doit le consulter pour savoir où en est le projet et le mettre à jour après chaque étape terminée.

Légende : `[x]` Terminé | `[/]` En cours | `[ ]` À faire

---

## Phase 1 : Socle, Dépendances & Documentation IA
- [x] Initialisation du projet Android (Kotlin + Jetpack Compose + Material 3)
- [x] Ajout des dépendances dans `gradle/libs.versions.toml` et `app/build.gradle.kts` :
  - CameraX (`1.6.2`)
  - Accompanist Permissions (`0.37.3`)
  - Tesseract4Android OpenMP (`4.9.0`)
  - Room (`2.8.5`) + plugin KSP (`2.3.12`)
- [x] Déclaration de la permission `CAMERA` et de `<uses-feature android:name="android.hardware.camera" />` dans `AndroidManifest.xml`
- [x] Mise en place de la documentation "Source de Vérité" (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`) avec référencement des Skills Android Studio

## Phase 2 : Persistance Locale avec Room (`data/local/` & `domain/`)
- [ ] Création du modèle métier `ScanRecord` dans `domain/`
- [ ] Création de l'entité Room `ScanHistoryEntity` et de ses fonctions de mapping vers `ScanRecord`
- [ ] Création du DAO `ScanHistoryDao` (observation réactive via `Flow`, insertion, suppression, gestion des favoris)
- [ ] Création de `AppDatabase` (`RoomDatabase`) et du `ScanHistoryRepository`

## Phase 3 : Modèle Tesseract & Couche OCR (`ocr/`)
- [ ] Intégration du fichier modèle `.traineddata` (Aurebesh) dans `app/src/main/assets/tessdata/`
- [ ] Création du gestionnaire d'assets (`TessDataManager`) pour copier `.traineddata` vers `filesDir/tesseract/tessdata/`
- [ ] Création du moteur OCR (`AurebeshOcrEngine`) encapsulant `TessBaseAPI` de manière thread-safe (`Mutex` / dispatcher dédié) et gérant son cycle de vie (`recycle()`)
- [ ] Utilitaires de prétraitement d'image (rotation, découpage de la zone de visée / ROI, binarisation/contraste si nécessaire)

## Phase 4 : Caméra & Permissions (`camera/` & `ui/`)
- [ ] Écran/composant de demande de permission caméra avec Accompanist (`rememberPermissionState`) et gestion des cas de refus (rationale / redirection vers les paramètres)
- [ ] Composant `CameraPreview` intégrant `PreviewView` et liant `Preview` + `ImageAnalysis` au `LifecycleOwner`
- [ ] Implémentation de l'`ImageAnalysis.Analyzer` pour extraire les frames sans bloquer le flux (`STRATEGY_KEEP_ONLY_LATEST`)

## Phase 5 : Interface Utilisateur & Expérience de Scan (`ui/scanner/` & `ui/history/`)
- [ ] Définition de `ScannerUiState`, `ScannerUiEvent` et `ScannerViewModel`
- [ ] Superposition visuelle (Overlay) avec cadre de visée (ROI) pour cibler le texte Aurebesh
- [ ] Affichage en temps réel (ou au déclenchement) du texte traduit et de l'indicateur de confiance
- [ ] Sauvegarde des traductions dans Room et écran/volet d'historique & favoris
- [ ] Options ergonomiques (pause/gel du scan, lampe torche, copie du texte traduit dans le presse-papiers)

## Phase 6 : Stabilisation, Tests & Optimisation
- [ ] Tests unitaires de la logique de traduction, des mappers Room et du `ViewModel`
- [ ] Vérification de l'absence de fuites mémoire (`ImageProxy`, `Bitmap`, `TessBaseAPI`)
- [ ] Ajustements de performance Compose et du prétraitement d'image en conditions réelles
