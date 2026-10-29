# DECISIONS.md — Journal des Décisions Techniques (ADR)

> Ce fichier consigne les choix d'architecture et les contraintes techniques clés pour éviter de remettre en question des décisions déjà arbitrées.

---

## ADR-001 : Choix de Tesseract4Android (variante OpenMP) pour l'OCR
- **Date** : 2026-10-29
- **Contexte** : L'alphabet Aurebesh (Star Wars) n'est pas supporté nativement par Google ML Kit Text Recognition. Il faut un moteur OCR capable de charger un fichier d'entraînement personnalisé (`.traineddata`).
- **Décision** : Utiliser `cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0`.
- **Conséquences** :
  - Excellente performance grâce au support multi-threading natif OpenMP.
  - Nécessite de copier les fichiers `.traineddata` depuis `assets/tessdata/` vers le stockage interne de l'application (`filesDir`), car l'API native C++ de Tesseract lit directement sur le système de fichiers.
  - `TessBaseAPI` n'est pas thread-safe côté appelant : un seul appel d'analyse doit être actif à la fois.

## ADR-002 : CameraX + Accompanist Permissions dans Jetpack Compose
- **Date** : 2026-10-29
- **Contexte** : L'application est 100 % Jetpack Compose et nécessite un flux caméra fluide couplé au cycle de vie ainsi qu'une gestion déclarative de la permission `android.permission.CAMERA`.
- **Décision** :
  - Utiliser `androidx.camera:camera-*` (`1.6.2`) avec `PreviewView` (via `AndroidView`) et `ImageAnalysis`.
  - Utiliser `com.google.accompanist:accompanist-permissions` (`0.37.3`) pour gérer l'état de la permission directement dans les Composables.
- **Conséquences** :
  - Configurer `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` pour que les frames caméra soient ignorées tant que Tesseract n'a pas terminé l'analyse de la frame en cours.
  - Fermer systématiquement chaque `ImageProxy` avec `.use { ... }`.

## ADR-003 : Documentation modulaire orientée IA & Catalogue de Skills (Source de Vérité)
- **Date** : 2026-10-29
- **Contexte** : Empêcher la saturation de la fenêtre de contexte de l'IA et maintenir une cohérence stricte tout au long du projet.
- **Décision** : Structurer la connaissance du projet en 5 fichiers Markdown légers et complémentaires (`AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/ROADMAP.md`, `docs/DECISIONS.md`) et référencer explicitement les Skills Android Studio pertinents dans `AGENTS.md`.
- **Conséquences** :
  - L'IA lit d'abord `AGENTS.md` puis uniquement les fichiers ou Skills pertinents pour la tâche en cours.
  - La cartographie des fichiers dans `docs/ARCHITECTURE.md` et l'avancement dans `docs/ROADMAP.md` doivent être tenus à jour à chaque modification.

## ADR-004 : Persistance locale avec AndroidX Room et KSP
- **Date** : 2026-10-29
- **Contexte** : L'utilisateur doit pouvoir conserver un historique local des textes Aurebesh traduits, marquer des favoris et les consulter hors-ligne de manière réactive.
- **Décision** :
  - Intégrer `androidx.room:room-runtime` et `androidx.room:room-ktx` (`2.8.5`) avec génération de code via `com.google.devtools.ksp` (`2.3.12`).
- **Conséquences** :
  - Utilisation de KSP (au lieu de KAPT obsolète) pour des temps de compilation rapides et une compatibilité native avec Kotlin `2.2.10` et AGP `9.5.0`.
  - Exposition des données sous forme de `Flow<List<ScanHistoryEntity>>` dans le DAO, mappées vers des objets `domain` immuables avant d'atteindre le `ViewModel` et l'UI Compose.

## ADR-005 : Génération sur mesure du modèle `aurebesh.traineddata`
- **Date** : 2026-10-29
- **Contexte** : Aucun fichier `.traineddata` officiel n'est fourni par défaut avec Tesseract pour l'alphabet Aurebesh, qui est une substitution mono-alphabétique directe vers l'alphabet latin.
- **Décision** : Générer un fichier `aurebesh.traineddata` dédié à partir d'une police Aurebesh (`.ttf`) via génération d'images synthétiques et des outils d'entraînement Tesseract (`tesstrain` / `lstmtraining` ou `text2image`).
- **Conséquences** :
  - Chaque glyphe Aurebesh est directement associé à son caractère latin équivalent (`A-Z`, `0-9`, ponctuation) dans le modèle `.traineddata`.
  - Tesseract renvoie ainsi directement le texte traduit en alphabet latin sans table de correspondance post-OCR complexe.

## ADR-006 : Détection automatique transparente de la polarité et du contraste (Zéro réglage utilisateur)
- **Date** : 2026-10-29
- **Contexte** : L'Aurebesh apparaît dans des conditions très variées : texte lumineux/coloré sur fond noir (écrans de jeux vidéo, hologrammes, enseignes néon) ou texte sombre sur fond clair (livres, panneaux imprimés). L'utilisateur ne doit avoir aucun bouton de réglage de contraste/inversion à manipuler.
- **Décision** : Implémenter un préprocesseur automatique (`AutoImagePreprocessor`) couplé à une stratégie de double-passe sur le score de confiance dans `AurebeshOcrEngine` :
  1. Conversion en niveaux de gris et étirement d'histogramme Min-Max (gère toutes les couleurs de néon/hologramme).
  2. Binarisation automatique par la méthode d'Otsu.
  3. Détection de la couleur du fond par analyse des pixels de bordure du ROI : si le fond est sombre (`> 50 %` de pixels noirs sur le contour), inversion automatique des pixels pour fournir à Tesseract du texte noir sur fond blanc.
  4. Si la confiance `meanConfidence` de Tesseract sur cette première passe est faible (`< 65 %`), exécution automatique d'une seconde passe sur la polarité opposée et sélection du résultat ayant la confiance maximale.
- **Conséquences** :
  - Expérience utilisateur 100 % transparente ("Point & Scan").
  - Très faible surcoût CPU : la seconde passe n'est déclenchée qu'en cas d'incertitude sur une petite image déjà découpée au ROI.

## ADR-007 : Optimisation du Pipeline Temps Réel Plein Écran, Gel par Tap & Épuration UI
- **Date** : 2026-10-29
- **Contexte** : Améliorer drastiquement la reconnaissance en conditions réelles, analyser l'ensemble de l'écran utile, figer l'image d'un simple tap et épurer l'interface.
- **Décision** :
  1. **Analyse Plein Écran & Cadrage `FILL_CENTER` HD (`CameraPreview` + `AutoImagePreprocessor`)** : Extension de `RoiRectFraction` à toute la surface utile de l'écran (`92 % x 66 %`), configuration d'un `ResolutionSelector` `1280x720` (`RATIO_16_9`), maintien de l'écran actif (`keepScreenOn = true`) et gel visuel instantané du `Bitmap` caméra par simple tap sur l'écran.
  2. **Binarisation Adaptative Locale par Image Intégrale (`AutoImagePreprocessor`)** : Utilisation du seuillage adaptatif local de Bradley-Roth (via Image Intégrale en $O(1)$ par pixel après filtre anti-Moiré $3 \times 3$) pour isoler uniquement les traits de caractères à fort contraste local et ignorer les grandes zones uniformes du décor en plein écran.
  3. **Score de Qualité Composite & `PSM_SINGLE_BLOCK` (`AurebeshOcrEngine`)** : Pondération de la confiance par la longueur utile du texte reconnu (`qualityScore`) pour comparer systématiquement la polarité normale et inversée.
  4. **Invalidation Automatique du Cache Modèle (`TessDataManager`) & Entraînement Augmenté (`train_aurebesh.py`)** : Vérification de l'empreinte SHA-256 de `aurebesh.traineddata` au démarrage et enrichissement du dataset synthétique (900 lignes augmentées caméra).
