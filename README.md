# 🛰️ OCR Aurebesh

**OCR Aurebesh** est une application Android 100 % hors-ligne permettant de **scanner et traduire instantanément du texte Aurebesh** via la caméra, ainsi que de **créer et exporter ses propres visuels en Aurebesh** grâce à l'**Aurebesh Studio** intégré.

---

## ✨ Fonctionnalités principales

### 📷 Scanner OCR Aurebesh en temps réel
* **Reconnaissance optique hors-ligne (Tesseract 5)** : modèle LSTM `aurebesh.traineddata` spécifiquement entraîné sur de multiples polices Aurebesh (pleines, contours, condensées, manuscrites) avec résistance au flou et aux faibles contrastes.
* **Prétraitement adaptatif multi-passes** : recadrage intelligent du viseur (ROI), seuillage adaptatif d'Otsu, détection d'inversion automatique de polarité (texte clair sur fond sombre ou inversement) et passe morphologique pour les polices en mode contour (*outline*).
* **Stabilisation temporelle & Gel d'image** :
  * Lissage temporel anti-scintillement pour éviter les sauts de texte lors des micro-mouvements.
  * **Tap sur l'écran** pour figer l'image instantanément (le viseur passe du bleu holocron au doré ambré) et déclencher une analyse haute précision multi-passes sur l'image nette.
  * **Pinch-to-zoom** fluide et contrôle du **flash / torche**.

### 🎨 Aurebesh Studio (Générateur & Exporteur)
* **Traduction et aperçu en direct** : saisissez votre texte en alphabet latin et visualisez-le instantanément en Aurebesh.
* **8 polices Aurebesh embarquées** :
  * *Aurebesh Classique*
  * *Aurebesh Bold*
  * *Aurebesh Italic*
  * *Aurebesh Condensed*
  * *Aurebesh Cantina (Droid)*
  * *Aurebesh Nexus (Tech)*
  * *Aurebesh Pixel*
  * *Aurebesh Calligraphie*
* **Personnalisation complète** :
  * Choix de la **taille**, de l'**alignement** (gauche, centré, droite) et de l'affichage optionnel du sous-titre en alphabet latin.
  * **Color Picker complet** pour le texte et l'arrière-plan : roue chromatique HSV, curseur de luminosité, **saisie directe du code Hexadécimal (`#RRGGBB`)** et **fond transparent** en un clic.
* **Exports professionnels & Partage** :
  * **PNG haute résolution** (avec support de la transparence alpha).
  * **JPG** haute qualité.
  * **SVG vectoriel (courbes de Bézier `<path>`)** : les glyphes Aurebesh sont convertis directement en tracés vectoriels natifs, exploitables immédiatement dans **Adobe Illustrator, Inkscape ou Figma** sans avoir besoin d'installer la police sur votre ordinateur.

### 🗂️ Historique & Sauvegarde locale
* Sauvegardez vos traductions scannées ou vos créations de l'Aurebesh Studio dans une base de données locale (**Room**).
* **Actions rapides** depuis l'historique : copier le texte, **ouvrir et modifier directement dans l'Aurebesh Studio**, ou supprimer (avec confirmation de sécurité).

### 🌍 Multilingue (4 langues)
* Interface intégralement traduite en :
  * 🇫🇷 **Français**
  * 🇬🇧 **English** (langue de repli / *fallback* par défaut)
  * 🇪🇸 **Español**
  * 🇵🇹 **Português**

---

## 🛠️ Stack Technique & Architecture

* **Langage** : Kotlin 2.x (`jvmTarget = 11`)
* **UI** : Jetpack Compose (Material 3), thème sur-mesure *Galactic HUD* (`HoloBlue`, `AurebeshGold`, `SpaceDark`), Splash Screen Android 12+ (`core-splashscreen`).
* **Architecture** : MVVM + Clean Architecture (`ui`, `camera`, `ocr`, `data`)
* **Caméra** : Jetpack CameraX (`CameraController`, `PreviewView`, `ImageAnalysis`)
* **Moteur OCR** : `tess-two` / `Tesseract4Android` (OpenMP) + pipeline de prétraitement Bitmap natif (`AutoImagePreprocessor`)
* **Persistance** : Room Database (`ScanHistoryDatabase`, Kotlin Coroutines & `Flow`)

---

## 🚀 Compilation et Installation

### Prérequis
* **Android Studio** (Ladybug / Meerkat ou plus récent)
* **JDK 17+**
* **Android SDK 36** (minSdk 26 / Android 8.0+)

### Commandes Gradle
```bash
# Compiler l'APK de debug
./gradlew :app:assembleDebug

# Lancer les tests unitaires
./gradlew :app:testDebugUnitTest
```

---

## 🔒 Confidentialité

**OCR Aurebesh** fonctionne **100 % hors-ligne** et ne collecte aucune donnée personnelle. Consultez la [Politique de Confidentialité](POLITIQUE_DE_CONFIDENTIALITE.md) pour plus de détails.

---

## 👤 Auteur

Développé avec passion par **Antoine « Crock » Hory**
* 🌐 Site web : [antoinehory.fr](https://antoinehory.fr)
* 📧 Contact : [contact@antoinehory.fr](mailto:contact@antoinehory.fr)
* ☕ Soutenir le projet : [PayPal.me/kuroku](https://paypal.me/kuroku)

> *Note légale : Ce projet est un outil indépendant créé par un fan et n'est en aucun cas affilié à, sponsorisé par, ou approuvé par Lucasfilm Ltd. ou The Walt Disney Company.*
