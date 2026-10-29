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
