# ARCHITECTURE.md — Architecture & Cartographie du Code

> Ce document décrit le Use Case de référence, l'architecture cible, le flux de données et l'emplacement exact des fichiers du projet **OCRAurebesh** afin d'éviter toute exploration inutile du codebase.

---

## 1. Use Case Nominal Validé

- **Acteur** : Utilisateur souhaitant traduire une inscription en alphabet Aurebesh (écran de jeu vidéo lumineux, affiche, parc d'attractions, support imprimé) vers l'alphabet latin.
- **Parcours nominal (Zéro réglage manuel)** :
  1. L'utilisateur ouvre **OCRAurebesh** et pointe la caméra vers l'inscription en Aurebesh en l'alignant dans le cadre de visée (ROI).
  2. Le pipeline d'analyse détecte **automatiquement et de façon transparente** la polarité (texte clair/néon sur fond sombre ou texte sombre sur fond clair) ainsi que le contraste, sans aucun réglage requis de la part de l'utilisateur.
  3. La traduction en lettres latines et son score de confiance s'affichent en direct sous le viseur.
  4. L'utilisateur peut figer/mettre en pause le scan, copier le texte ou le sauvegarder dans l'historique local (Room) et ses favoris.

---

## 2. Vue d'Ensemble (Clean MVVM, UDF & Persistance Locale)

```mermaid
flowchart LR
    Camera["CameraX (Preview + ImageAnalysis)"] -->|ImageProxy| Analyzer["AurebeshImageAnalyzer"]
    Analyzer -->|Bitmap ROI| Preprocessor["AutoImagePreprocessor (Otsu + Polarité Auto)"]
    Preprocessor -->|Bitmap Normalisé + Inversé| OcrEngine["AurebeshOcrEngine (TessBaseAPI)"]
    OcrEngine -->|OcrResult (Meilleure Confiance)| VM["ScannerViewModel"]
    VM <-->|Sauvegarde / Flow Historique| Repo["ScanHistoryRepository"]
    Repo <-->|Suspend / Flow| Dao["ScanHistoryDao (Room)"]
    VM -->|StateFlow<ScannerUiState>| UI["Compose UI (ScannerScreen / History)"]
    UI -->|UiEvent| VM
```

---

## 3. Organisation des Packages (`fr.antoinehory.ocraurebesh`)

```text
fr.antoinehory.ocraurebesh/
├── MainActivity.kt              # Point d'entrée unique, configuration Edge-to-Edge
├── ui/
│   ├── theme/                   # Thème Material 3 (Color, Theme, Type)
│   ├── components/              # Composants UI réutilisables (Overlay viseur, carte résultat, demande permission)
│   ├── scanner/                 # Écran principal de scan (ScannerScreen, ScannerViewModel, ScannerUiState)
│   └── history/                 # Écran / BottomSheet d'historique et favoris des traductions sauvegardées
├── camera/                      # Intégration CameraX et ImageAnalysis.Analyzer
├── ocr/                         # Gestion des assets tessdata, AutoImagePreprocessor et AurebeshOcrEngine
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Instance RoomDatabase
│   │   ├── dao/                 # Interfaces @Dao (ScanHistoryDao)
│   │   └── entity/              # Entités Room (@Entity ScanHistoryEntity)
│   └── repository/              # Repositories (ScanHistoryRepository)
└── domain/                      # Modèles métier purs (OcrResult, ScanRecord)
```

---

## 4. Cartographie des Fichiers Actuels

*Mettre à jour ce tableau à chaque création, modification structurante ou suppression de fichier.*

| Chemin relatif | Responsabilité | Statut |
| :--- | :--- | :--- |
| `AGENTS.md` | Point d'entrée IA, index documentaire, catalogue de skills et garde-fous | Actif |
| `docs/ARCHITECTURE.md` | Use Case, architecture MVVM/UDF, pipeline OCR auto-adaptatif et cartographie | Actif |
| `docs/CODING_STANDARDS.md` | Standards de code Kotlin/Compose et bonnes pratiques CameraX/Tesseract/Room | Actif |
| `docs/ROADMAP.md` | Suivi d'avancement par phases (incluant génération `.traineddata`) | Actif |
| `docs/DECISIONS.md` | Journal des décisions d'architecture (ADR-001 à ADR-006) | Actif |
| `build.gradle.kts` | Configuration Gradle racine (plugins Android Application, Kotlin Compose, KSP) | Actif |
| `gradle/libs.versions.toml` | Catalogue des versions (CameraX, Accompanist, Tesseract, Room, KSP) | Actif |
| `app/build.gradle.kts` | Configuration du module `:app` (minSdk 29, targetSdk 37, Compose, KSP, Room) | Actif |
| `app/src/main/AndroidManifest.xml` | Déclaration permission `CAMERA`, feature camera et `MainActivity` | Actif |
| `app/src/main/assets/tessdata/aurebesh.traineddata` | Modèle LSTM Tesseract 5 entraîné sur mesure (Regular, Bold, Condensed, quantifié 395 Ko) | Actif |
| `tools/tessdata-trainer/Dockerfile` | Image Docker Ubuntu 24.04 + Tesseract 5 + Python pour l'entraînement du modèle | Actif |
| `tools/tessdata-trainer/train_aurebesh.py` | Script reproductible de génération synthétique et d'entraînement LSTM Aurebesh | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/MainActivity.kt` | Activité hôte Jetpack Compose branchant `ScannerViewModel` et `ScannerRoute` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/domain/ScanRecord.kt` | Modèle métier immuable (`@Immutable`) d'un scan Aurebesh | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/domain/OcrResult.kt` | Interface scellée (`Success`, `Empty`, `Error`) résultat d'une analyse OCR | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ocr/TessDataManager.kt` | Extraction atomique de `aurebesh.traineddata` vers `filesDir/tesseract/tessdata/` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ocr/AutoImagePreprocessor.kt` | Découpage ROI, étirement d'histogramme 2%-98%, binarisation d'Otsu et détection auto de polarité | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ocr/AurebeshOcrEngine.kt` | Interface `OcrRecognizer` et wrapper thread-safe (`Mutex`) de `TessBaseAPI` avec double-passe transparente | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/camera/AurebeshImageAnalyzer.kt` | `ImageAnalysis.Analyzer` extrayant le ROI avec fermeture garantie de l'`ImageProxy` (`use {}`) et recyclage `Bitmap` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/camera/CameraPreview.kt` | Composable CameraX (`PreviewView` + `ImageAnalysis` `STRATEGY_KEEP_ONLY_LATEST` + Torche) | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/components/CameraPermissionGate.kt` | Garde-barrière Accompanist Permissions (`CAMERA`) avec gestion rationale et refus permanent | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/components/ScannerOverlay.kt` | Overlay du viseur HUD dessiné avec `Modifier.drawWithCache` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/scanner/ScannerUiState.kt` | `ScannerUiState` et `ScannerUiEvent` (pattern UDF) | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/scanner/ScannerViewModel.kt` | `ViewModel` orchestrant l'OCR, la caméra et l'historique Room | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/scanner/ScannerScreen.kt` | `ScannerRoute` et `ScannerScreen` Edge-to-Edge avec carte de traduction et `@Preview` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/history/ScanHistorySheet.kt` | `ModalBottomSheet` d'historique et favoris avec `LazyColumn` optimisée (`key` + `contentType`) | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/data/local/entity/ScanHistoryEntity.kt` | Entité Room `scan_history` et fonctions de mapping `toDomain()` / `toEntity()` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/data/local/dao/ScanHistoryDao.kt` | Interface `@Dao` réactive (`Flow` + `suspend`) pour l'historique et les favoris | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/data/local/AppDatabase.kt` | Singleton `RoomDatabase` (`ocr_aurebesh.db`) | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/data/repository/ScanHistoryRepository.kt` | Repository exposant les `Flow<List<ScanRecord>>` et opérations CRUD | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/theme/Color.kt` | Palette de couleurs Material 3 | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/theme/Theme.kt` | `OCRAurebeshTheme` | Actif |
| `app/src/main/java/fr/antoinehory/ocraurebesh/ui/theme/Type.kt` | Typographies Material 3 | Actif |
| `app/src/test/java/fr/antoinehory/ocraurebesh/ocr/AutoImagePreprocessorTest.kt` | Tests unitaires JVM d'Otsu, de l'étirement d'histogramme et de la détection auto de polarité | Actif |
| `app/src/test/java/fr/antoinehory/ocraurebesh/data/ScanHistoryMapperAndRepositoryTest.kt` | Tests unitaires des mappers `toDomain`/`toEntity` et de `ScanHistoryRepository` | Actif |
| `app/src/test/java/fr/antoinehory/ocraurebesh/ui/scanner/ScannerViewModelTest.kt` | Tests unitaires de `ScannerViewModel` avec Fakes (`FakeOcrRecognizer`, `FakeScanHistoryDao`) | Actif |
| `app/src/androidTest/java/fr/antoinehory/ocraurebesh/data/local/ScanHistoryDaoInstrumentedTest.kt` | Tests instrumentés SQLite en mémoire de `ScanHistoryDao` | Actif |

---

## 5. Pipeline OCR Auto-Adaptatif & Caméra (Spécifications Techniques)

1. **Génération & Initialisation des données Tesseract (`tessdata`)** :
   - Fichier modèle cible : `app/src/main/assets/tessdata/aurebesh.traineddata`.
   - Au démarrage, `TessDataManager` copie `aurebesh.traineddata` vers `context.filesDir/tesseract/tessdata/` s'il est absent.
   - `TessBaseAPI` est initialisé avec `init(dataPath, "aurebesh")` et une whitelist de caractères latins (`A-Z0-9.,!?-' `).

2. **Capture & Découpage ROI (`camera/` -> `ocr/`)** :
   - `ImageAnalysis` configuré en `STRATEGY_KEEP_ONLY_LATEST`.
   - Conversion de l'`ImageProxy` en `Bitmap`, rotation selon `imageProxy.imageInfo.rotationDegrees`, et découpage strict de la zone de visée (ROI). Fermeture immédiate de l'`ImageProxy` via `use {}`.

3. **Prétraitement Automatique Transparent (`AutoImagePreprocessor`)** :
   - **Aucun réglage manuel requis par l'utilisateur** pour gérer les textes clairs sur fond sombre (écrans, néons) ou sombres sur fond clair :
     1. **Conversion en niveaux de gris & Normalisation Min-Max** : étirement de l'histogramme de luminance sur `[0..255]` pour maximiser le contraste, quelle que soit la couleur du texte (bleu hologramme, jaune, rouge, blanc).
     2. **Binarisation adaptative (Méthode d'Otsu)** : calcul automatique du seuil optimal séparant le fond et les glyphes.
     3. **Détection automatique de la polarité par les bordures** : analyse de la proportion de pixels sombres sur le périmètre (bordures) du rectangle ROI. Si les bordures sont majoritairement sombres (fond foncé avec texte clair), le `Bitmap` binarisé est automatiquement inversé pour présenter à Tesseract du texte noir sur fond blanc.
     4. **Double-passe de sécurité (Fallback sur confiance)** : si le score `meanConfidence` retourné par Tesseract sur la polarité détectée est inférieur au seuil de fiabilité (ex. `< 65 %`), `AurebeshOcrEngine` teste automatiquement la version inversée dans la même passe et conserve le résultat ayant le `meanConfidence` le plus élevé.

---

## 6. Persistance Locale avec Room (`data/local/`)

- **Entité principale (`ScanHistoryEntity`)** :
  - `id: Long` (Clé primaire auto-générée)
  - `translatedText: String` (Texte traduit depuis l'Aurebesh)
  - `confidence: Int` (Score de confiance moyen Tesseract 0-100)
  - `createdAt: Long` (Timestamp epoch millis)
  - `isFavorite: Boolean` (Marquage favori par l'utilisateur)
- **DAO (`ScanHistoryDao`)** :
  - `observeAll(): Flow<List<ScanHistoryEntity>>` (Trié par `createdAt DESC`)
  - `observeFavorites(): Flow<List<ScanHistoryEntity>>`
  - `insert(entity: ScanHistoryEntity): Long` (`suspend`)
  - `toggleFavorite(id: Long, isFavorite: Boolean)` (`suspend`)
  - `deleteById(id: Long)` et `clearAll()` (`suspend`)
