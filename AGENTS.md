# AGENTS.md — Source de Vérité Principale (OCR Aurebesh)

> **RÈGLE CRITIQUE POUR L'AGENT IA :**
> Ce fichier est ton point d'entrée obligatoire. Pour préserver ta fenêtre de contexte et garantir un code cohérent, **ne lis pas l'intégralité du codebase à l'aveugle**. Consulte d'abord cet index et uniquement le fichier de documentation ou le Skill correspondant à ta tâche.

---

## 1. Index des Sources de Vérité

| Fichier | Rôle | Quand le lire / mettre à jour ? |
| :--- | :--- | :--- |
| [`AGENTS.md`](./AGENTS.md) | Règles globales, stack technique, skills, garde-fous | Au début de chaque tâche |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Structure des packages, flux de données, index des fichiers | Avant de créer/modifier une classe ou l'architecture |
| [`docs/CODING_STANDARDS.md`](./docs/CODING_STANDARDS.md) | Standards Kotlin/Compose, bonnes pratiques CameraX/Tesseract/Room | Avant d'écrire ou refactoriser du code |
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | État d'avancement, tâches faites / en cours / à venir | Au début et à la fin de chaque fonctionnalité |
| [`docs/DECISIONS.md`](./docs/DECISIONS.md) | Décisions techniques (ADR), configuration OCR, CameraX & Room | Lors d'un choix technique ou d'un arbitrage |

---

## 2. Protocole d'Économie de Contexte

1. **Ciblage chirurgical** : Utilise la cartographie dans [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) pour identifier directement le fichier concerné sans scanner tout le projet.
2. **Lectures partielles** : Utilise les outlines de fichiers (`view_file_outline`) ou des plages de lignes (`startLine`/`endLine`) sur les fichiers volumineux.
3. **Mise à jour continue** :
   - Dès qu'un nouveau fichier Kotlin est créé ou déplacé, mets à jour la cartographie dans [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).
   - Dès qu'une étape est validée, coche-la dans [`docs/ROADMAP.md`](./docs/ROADMAP.md).
   - Dès qu'une décision structurante est prise, ajoute une entrée concise dans [`docs/DECISIONS.md`](./docs/DECISIONS.md).

---

## 3. Skills Agentiques à Activer selon la Tâche

Avant d'implémenter l'un des sujets ci-dessous, **lis le fichier `SKILL.md` correspondant** via `read_file` pour appliquer les patterns officiels Android Studio :

| Domaine d'intervention | Skill à activer | Chemin du Skill |
| :--- | :--- | :--- |
| **Caméra & Analyse d'image** (CameraX, `PreviewView`, `ImageAnalysis`) | `camerax` | `C:\Users\hory\.android-studio\skills\camerax\SKILL.md` |
| **Insets & Affichage plein écran** (Barre d'état/navigation, caméra plein écran) | `edge-to-edge` | `C:\Users\hory\.android-studio\skills\edge-to-edge\SKILL.md` |
| **Performance UI Compose** (Stabilité, recompositions sur flux caméra/OCR) | `jetpack-compose-performance` | `C:\Users\hory\.android-studio\skills\jetpack-compose-performance\SKILL.md` |
| **Audit ANR & Threading** (Opérations lourdes OCR, E/S fichiers, Room) | `anr-audit-and-fix` | `C:\Users\hory\.android-studio\skills\anr-audit-and-fix\SKILL.md` |
| **Sécurité des Permissions** (`CAMERA`, gestion du refus et rationale) | `android-permissions-security` | `C:\Users\hory\.android-studio\skills\android-permissions-security\SKILL.md` |
| **UI Adaptative** (Rotation d'écran, tablettes, pliables) | `adaptive` | `C:\Users\hory\.android-studio\skills\adaptive\SKILL.md` |
| **Tests Unitaires & UI** (JUnit, Compose UI Test, DAO tests) | `testing-setup` | `C:\Users\hory\.android-studio\skills\testing-setup\SKILL.md` |

---

## 4. Stack Technique de Référence

- **Package racine** : `fr.antoinehory.ocraurebesh`
- **SDK** : `minSdk = 29`, `targetSdk = 37`, `compileSdk = 37`
- **Langage & Build** : Kotlin `2.2.10`, AGP `9.5.0`, KSP `2.3.12`, Java 11, Version Catalog (`gradle/libs.versions.toml`)
- **UI** : Jetpack Compose (BOM `2026.02.01`), Material 3, Edge-to-Edge activé
- **Permissions** : Accompanist Permissions (`0.37.3`) pour `android.permission.CAMERA`
- **Caméra** : AndroidX CameraX (`1.6.2`) — `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`
- **OCR** : Tesseract4Android OpenMP (`4.9.0`) — `cz.adaptech.tesseract4android:tesseract4android-openmp`
- **Persistance Locale** : AndroidX Room (`2.8.5`) — `room-runtime`, `room-ktx`, `room-compiler` (via KSP)

---

## 5. Garde-Fous Critiques (Résumé)

> Pour le détail complet des règles de code, consulter [`docs/CODING_STANDARDS.md`](./docs/CODING_STANDARDS.md).

1. **MVVM & UDF strict** : État immuable exposé via `StateFlow` (`collectAsStateWithLifecycle()`), Composables sans logique métier/OCR/BDD.
2. **Thread-safety Tesseract** : `TessBaseAPI` n'est pas thread-safe. Exécuter `init`, `setImage` et `utF8Text` hors du Main Thread via un `Mutex` ou `limitedParallelism(1)`.
3. **Zéro fuite native** :
   - Toujours fermer les `ImageProxy` avec `imageProxy.use { ... }`.
   - Toujours configurer `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.
   - Appeler `tessBaseApi.recycle()` à la libération des ressources.
4. **Persistance Room propre** :
   - Exposer des `Flow<List<...>>` depuis les `@Dao` pour l'observation réactive et utiliser des fonctions `suspend` pour les écritures.
   - Ne jamais exposer directement les `@Entity` Room dans les Composables (mapper vers des modèles du package `domain`).
