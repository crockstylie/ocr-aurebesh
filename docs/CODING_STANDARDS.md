# CODING_STANDARDS.md — Standards de Code & Best Practices

> Ce document définit les conventions de code, les idiomes Kotlin/Compose et les bonnes pratiques obligatoires pour garantir un code propre, performant, testable et maintenable sur **OCRAurebesh**.

---

## 1. Conventions Kotlin & Style

- **Langue du code** :
  - Identifiants (classes, fonctions, propriétés, paramètres, tables SQL) en **anglais** clair et explicite (`ScanHistoryEntity`, `analyzeFrame`, `confidenceScore`).
  - Documentation KDoc et commentaires explicatifs en **français** (ou anglais technique cohérent).
- **Immuabilité par défaut** :
  - Toujours privilégier `val` à `var`.
  - Utiliser des collections immuables (`List`, `Set`, `Map`) dans les modèles exposés à l'UI.
- **Null-safety & Validation** :
  - Proscrire l'opérateur `!!` (non-null assertion). Utiliser `?:`, `let`, `requireNotNull()` ou des retours anticipés (`?: return`).
- **Gestion des erreurs** :
  - Utiliser `runCatching { ... }` / `Result<T>` ou des hiérarchies scellées (`sealed interface`) pour représenter les états de succès, chargement et erreur.
  - Ne jamais avaler une `CancellationException` dans une coroutine (toujours la relancer si on utilise un `catch (e: Exception)` générique).

---

## 2. Coroutines, Concurrence & Threading

- **Injection des Dispatchers** :
  - Ne jamais coder en dur `Dispatchers.IO` ou `Dispatchers.Default` à l'intérieur des classes métier/repositories ; les passer au constructeur (avec valeur par défaut) pour faciliter les tests unitaires.
- **Isolation du Main Thread (Zéro ANR)** :
  - **Strictement interdit sur le Main Thread** : copie des assets `.traineddata`, initialisation de `TessBaseAPI`, conversion/découpage/binarisation de `Bitmap`, exécution OCR (`utF8Text`), et requêtes E/S bloquantes.
- **Accès séquentiel à Tesseract** :
  - Protéger toutes les interactions avec l'instance `TessBaseAPI` via un `Mutex` (`kotlinx.coroutines.sync.Mutex`) ou un `CoroutineDispatcher` limité à 1 thread (`Dispatchers.Default.limitedParallelism(1)`).

---

## 3. Jetpack Compose & UI Best Practices

- **State Hoisting (Hissage d'état)** :
  - Séparer systématiquement chaque écran en deux niveaux :
    1. `ScannerRoute(viewModel: ScannerViewModel)` : collecte les `StateFlow` avec `collectAsStateWithLifecycle()` et câble la navigation/permissions.
    2. `ScannerScreen(uiState: ScannerUiState, onEvent: (ScannerUiEvent) -> Unit, modifier: Modifier = Modifier)` : Composable **stateless**, pur, facile à prévisualiser (`@Preview`) et à tester.
- **Zéro réglage technique exposé à l'utilisateur** :
  - L'interface de scan doit rester épurée : ne pas exposer de sliders de seuillage ou de switch "texte clair / fond foncé". Toute l'adaptation d'image se fait automatiquement dans `AutoImagePreprocessor` et `AurebeshOcrEngine`.
- **Standards UX / UI : Minimalisme Textuel & Cohérence Contextuelle** :
  - **Moins de texte possible** : Ne pas afficher de phrases tutoriels ou explicatives superflues (ex. *"Ajustez le cadre..."*) lorsque les affordances visuelles (poignées d'angle, bordures HUD) suffisent à guider l'utilisateur.
  - **Zéro redondance de contrôles** : Un contrôle n'a qu'un seul emplacement légitime. Ne jamais dupliquer un bouton entre le viseur et le dock inférieur.
  - **Séparation stricte contenu / actions** : La carte de texte traduit affiche uniquement le texte ou le placeholder. Les boutons d'actions sont regroupés dans une barre contextuelle distincte (`ScannerContextualToolbar`), située immédiatement au-dessus.
  - **Visibilité contextuelle des actions** :
    - En direct : aucun bouton si rien n'est reconnu ; boutons `[Copier]`, `[Sauvegarder]`, `[Effacer]` dès qu'un texte est présent.
    - En figé : `[Play]` tout à gauche et `[Analyser]` à côté ; les boutons de texte n'apparaissent à droite que si du texte est reconnu.
- **Stabilité & Performance de Recomposition** :
  - Annoter les classes d'état avec `@Immutable` ou `@Stable` dès lors qu'elles contiennent des types que le compilateur Compose ne peut pas inférer comme stables.
  - Toujours fournir une `key` unique dans les `LazyColumn` (`items(items = historyList, key = { it.id })`).
  - Différer la lecture des états qui changent fréquemment (animations, coordonnées) dans les phases de layout ou de dessin (`Modifier.offset { ... }`, `Modifier.drawWithContent { ... }`).
- **Modificateurs (`Modifier`)** :
  - Tout Composable réutilisable doit accepter `modifier: Modifier = Modifier` comme premier paramètre optionnel et l'appliquer à son élément racine.
- **Edge-to-Edge** :
  - Respecter les insets système (`Scaffold` `innerPadding`, `WindowInsets.safeDrawing`, `systemBarsPadding()`) pour qu'aucun bouton ou texte traduit ne soit masqué par la barre de statut ou de navigation.

---

## 4. CameraX & Prétraitement d'Image Automatique

- **Cycle de vie** :
  - Lier le `ProcessCameraProvider` au `LocalLifecycleOwner.current` dans un `LaunchedEffect` ou via un contrôleur dédié, et appeler `unbindAll()` avant un nouveau bind.
- **Gestion stricte des `ImageProxy`** :
  - Toujours utiliser `imageProxy.use { proxy -> ... }` dans `ImageAnalysis.Analyzer.analyze()` pour garantir la fermeture du buffer même en cas d'exception.
- **Optimisation CPU/Mémoire du `AutoImagePreprocessor`** :
  - Découper **d'abord** la région d'intérêt (ROI correspondant au rectangle de visée) avant d'exécuter l'étirement d'histogramme et la binarisation d'Otsu afin de ne traiter qu'un petit nombre de pixels.
  - Manipuler les pixels via un `IntArray` (`bitmap.getPixels(...)` / `bitmap.setPixels(...)`) plutôt que des appels répétés à `getPixel(x, y)` (gain de performance d'un facteur 10x à 50x en Kotlin).

---

## 5. Persistance Locale (Room)

- **Séparation Entité / Modèle de Domaine** :
  - Les classes `@Entity` (ex. `ScanHistoryEntity` dans `data/local/entity/`) restent dans la couche `data`.
  - Fournir des fonctions d'extension de mapping (`fun ScanHistoryEntity.toDomain(): ScanRecord`, `fun ScanRecord.toEntity(): ScanHistoryEntity`) pour ne pas coupler l'UI aux annotations Room.
- **DAOs Réactifs & Asynchrones** :
  - Les lectures de listes ou d'états observés retournent `Flow<List<ScanHistoryEntity>>`.
  - Les opérations d'écriture (`@Insert`, `@Update`, `@Delete`, `@Query` ponctuel) sont toujours déclarées `suspend`.
- **Schéma & Migrations** :
  - Toujours typer explicitement les noms de tables (`tableName = "scan_history"`) et de colonnes (`@ColumnInfo(name = "...")`).
  - Indexer les colonnes fréquemment filtrées ou triées (ex. `created_at`, `is_favorite`).
