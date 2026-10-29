# 📋 Changelog — OCR Aurebesh

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

---

## [1.0.0] - 2026-04-20

### 🎉 Version initiale (Première publication Google Play)

#### 📷 Scanner OCR Aurebesh
- Intégration du moteur OCR hors-ligne **Tesseract** avec modèle `aurebesh.traineddata` sur-mesure (multi-polices, multi-lignes, tolérance au flou et aux polices en contour *outline*).
- Pipeline de prétraitement d'image automatique (`AutoImagePreprocessor`) : découpe de la zone du viseur (ROI), seuillage d'Otsu, inversion de polarité automatique et fermeture morphologique.
- Stabilisation temporelle des résultats en direct pour éliminer le scintillement lors des micro-mouvements.
- Gel d'image par simple **tap sur l'écran** avec changement de couleur du viseur (Bleu Holocron ➔ Or Aurebesh) et ré-analyse haute précision multi-passes sur l'image figée.
- Conservation de l'image figée lors de la rotation de l'écran.
- Support du **Pinch-to-Zoom** et du **Flash / Torche**.

#### 🎨 Aurebesh Studio (Générateur de texte)
- Studio de création permettant de convertir du texte latin en Aurebesh en temps réel.
- **8 polices Aurebesh** intégrées (*Classique, Bold, Italic, Condensed, Cantina, Nexus, Pixel, Calligraphie*).
- Sélecteur de couleurs avancé (roue HSV, luminosité, saisie directe du code **Hexadécimal `#RRGGBB`**) pour le texte et le fond, avec option **Fond transparent**.
- Réglage de la taille de police, de l'alignement (gauche, centré, droite) et de l'affichage du sous-titre latin.
- **Export PNG, JPG et SVG vectoriel natif** : conversion des glyphes Aurebesh en tracés vectoriels (`<path>`) directement éditables sur Adobe Illustrator, Inkscape ou Figma sans nécessiter l'installation de la police.
- Enregistrement direct dans le dossier `Téléchargements/OCRAurebesh` et partage rapide.

#### 🗂️ Historique & Expérience Utilisateur (HUD Galactique)
- Sauvegarde locale (Room) des textes scannés ainsi que des créations de l'Aurebesh Studio.
- Feuille d'historique accessible depuis le Scanner et depuis l'Aurebesh Studio, permettant de copier un texte, de l'**éditer directement dans l'Aurebesh Studio**, ou de le supprimer (avec boîte de dialogue de confirmation).
- Notifications internes discrètes (*HudSnackbar*) au style HUD galactique en haut de l'écran avec fermeture automatique.
- Page **À propos / Crédits** avec liens vers les réseaux sociaux, bouton de notation 5 étoiles sur le Google Play Store et lien de soutien PayPal.
- Nouveau **Splash Screen** Android 12+ et icônes adaptatives sur-mesure.
- **Traduction complète en 4 langues** : Français (`fr`), Anglais (`en` + langue par défaut), Espagnol (`es`) et Portugais (`pt`).
