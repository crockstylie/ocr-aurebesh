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
- [x] Validation du Use Case nominal et de l'architecture auto-adaptative (polarité/contraste automatiques)

## Phase 2 : Génération & Intégration du Modèle `aurebesh.traineddata`
- [x] Récupération des polices Aurebesh libres de droits (`Aurebesh.ttf`, `Aurebesh-Bold.ttf`, `Aurebesh-Condensed.ttf`) et préparation du corpus d'entraînement (`A-Z`, `0-9`)
- [x] Script/outillage Docker (`tools/tessdata-trainer/`) de génération synthétique (500 lignes `.tif` + `.box` / `.lstmf`) et compilation du modèle `aurebesh.traineddata` quantifié (100 % de précision sur les phrases de validation)
- [x] Intégration du fichier généré `aurebesh.traineddata` (`395 Ko`) dans `app/src/main/assets/tessdata/aurebesh.traineddata`

## Phase 3 : Persistance Locale avec Room (`data/local/` & `domain/`)
- [x] Création du modèle métier `ScanRecord` dans `domain/`
- [x] Création de l'entité Room `ScanHistoryEntity` et de ses fonctions de mapping vers `ScanRecord`
- [x] Création du DAO `ScanHistoryDao` (observation réactive via `Flow`, insertion, suppression, gestion des favoris)
- [x] Création de `AppDatabase` (`RoomDatabase`) et du `ScanHistoryRepository`

## Phase 4 : Couche OCR & Prétraitement Automatique (`ocr/`)
- [x] Création du gestionnaire d'assets (`TessDataManager`) pour copier `aurebesh.traineddata` vers `filesDir/tesseract/tessdata/`
- [x] Création de `AutoImagePreprocessor` :
  - Découpage de la région d'intérêt (ROI) et rotation
  - Normalisation de contraste (étirement d'histogramme Min-Max en niveaux de gris)
  - Binarisation automatique (algorithme d'Otsu)
  - Détection automatique de polarité (analyse des bordures du ROI pour inverser automatiquement le texte clair sur fond sombre)
- [x] Création du moteur OCR (`AurebeshOcrEngine`) :
  - Encapsulation thread-safe de `TessBaseAPI` (`Mutex` / dispatcher dédié) et cycle de vie (`recycle()`)
  - Logique de double-passe transparente (fallback sur l'image inversée si `meanConfidence` est faible, en retenant le meilleur score)

## Phase 5 : Caméra & Permissions (`camera/` & `ui/`)
- [x] Écran/composant de demande de permission caméra avec Accompanist (`rememberPermissionState`) et gestion des cas de refus (rationale / redirection vers les paramètres)
- [x] Composant `CameraPreview` intégrant `PreviewView` et liant `Preview` + `ImageAnalysis` au `LifecycleOwner`
- [x] Implémentation de `AurebeshImageAnalyzer` (`ImageAnalysis.Analyzer`) avec `STRATEGY_KEEP_ONLY_LATEST`

## Phase 6 : Interface Utilisateur & Expérience de Scan (`ui/scanner/` & `ui/history/`)
- [x] Définition de `ScannerUiState`, `ScannerUiEvent` et `ScannerViewModel`
- [x] Superposition visuelle (Overlay) avec cadre de visée (ROI) pour cibler le texte Aurebesh
- [x] Affichage en temps réel du texte traduit et de l'indicateur de confiance (sans réglages complexes pour l'utilisateur)
- [x] Sauvegarde des traductions dans Room et écran/volet d'historique & favoris
- [x] Options ergonomiques (pause/gel du scan, lampe torche, copie du texte traduit dans le presse-papiers)

## Phase 7 : Stabilisation, Tests & Optimisation
- [x] Tests unitaires de `AutoImagePreprocessor` (vérification de la détection clair/sombre et d'Otsu), des mappers Room et du `ViewModel`
- [x] Vérification de l'absence de fuites mémoire (`ImageProxy`, `Bitmap`, `TessBaseAPI`)
- [x] Ajustements de performance Compose et validation sur des images Aurebesh réelles (écrans et imprimés)

## Phase 8 : Studio / Générateur Aurebesh (`ui/generator/`)
- [x] Intégration de 9 polices Aurebesh (`Regular`, `Bold`, `Italic`, `Condensed`, `Canon AF`, `Cantina`, `Droid`, `Droid Depot`, `Hand`) dans `app/src/main/assets/fonts/`
- [x] Création de `AurebeshGeneratorActivity` accessible depuis un bouton HUD dans `ScannerScreen` (tout en conservant l'ouverture directe de l'application sur le scanner)
- [x] Création de `AurebeshGeneratorScreen` épuré : barre supérieure sans titre, bouton Télécharger unique avec sous-menu (`SVG`, `PNG`, `JPG`), grand champ multi-lignes sans label, menu de style/couleurs rétractable à la demande, pastille damier blanc/gris DAO pour la transparence et Color Picker HSV interactif
- [x] Export et téléchargement en **SVG vectorisé** (tracés de courbes `<path d="..." />` par caractère directement éditables dans Illustrator sans police installée), **PNG** et **JPG** dans `Téléchargements/OCRAurebesh` via `MediaStore.Downloads`
- [x] Notification système de téléchargement avec aperçu de l'image et boutons d'action **Partager** / **Ouvrir** (`POST_NOTIFICATIONS` + `Intent.ACTION_SEND` / `Intent.ACTION_VIEW`)
- [x] Ré-entraînement Docker (`aurebesh.traineddata`) et pipeline multi-échelle Full HD supportant les paragraphes denses (20 lignes de 30 caractères), le flou optique, les faibles contrastes et les lettres en mode contour





