#!/usr/bin/env python3
"""
Script d'entraînement sur mesure du modèle Tesseract `aurebesh.traineddata`
pour l'application Android OCRAurebesh.
"""
import os
import random
import shutil
import subprocess
import urllib.request
import urllib.parse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WORK_DIR = Path("/tmp/aurebesh_build")
FONTS_DIR = WORK_DIR / "fonts"
LANGDATA_DIR = WORK_DIR / "langdata"
GT_DIR = WORK_DIR / "gt"
OUTPUT_DIR = WORK_DIR / "output"
FINAL_MODEL_PATH = Path("/workspace/output/aurebesh.traineddata")

FONT_URLS = {
    "Aurebesh.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh.ttf",
    "Aurebesh-Bold.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh%20Bold.ttf",
    "Aurebesh-Condensed.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh%20Condensed.ttf",
}

TESSDATA_BEST_ENG_URL = "https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata"
RADICAL_STROKE_URL = "https://raw.githubusercontent.com/tesseract-ocr/langdata_lstm/main/radical-stroke.txt"

CORPUS_WORDS = [
    "MAY", "THE", "FORCE", "BE", "WITH", "YOU", "ALWAYS",
    "STAR", "WARS", "AUREBESH", "GALAXY", "EMPIRE", "REBEL", "ALLIANCE",
    "JEDI", "SITH", "MANDALORIAN", "CORUSCANT", "TATOOINE", "NABOO",
    "BESPIN", "ENDOR", "HOTH", "MUSTAFAR", "KAMINO", "KASHYYYK",
    "DOCKING", "BAY", "94", "SECTOR", "7G", "LEVEL", "1313", "HANGAR", "42",
    "CAUTION", "DANGER", "BLAST", "DOORS", "CONTROL", "ROOM", "REACTOR",
    "HYPERDRIVE", "FALCON", "XWING", "TIE", "FIGHTER", "DROID", "ASTROMECH",
    "R2D2", "C3PO", "BB8", "SKYWALKER", "VADER", "KENOBI", "AHSOKA", "ANDOR",
    "CANTINA", "BOUNTY", "HUNTER", "CREDITS", "IMPERIAL", "REPUBLIC",
    "QUARANTINE", "AUTHORIZED", "PERSONNEL", "ONLY", "EXIT", "ENTRANCE",
    "CARGO", "HOLD", "MEDICAL", "BRIDGE", "ARMORY", "SHIELD", "GENERATOR",
    "EDGE", "DOCKING", "DROID", "ORDER", "RADAR", "UNDER", "GUARD", "DARK",
    "DOOR", "RED", "RIDGE", "DANGER", "YOUR", "YOU", "YOUNG", "YODA", "YAVIN",
    "PACK", "MY", "BOX", "WITH", "FIVE", "DOZEN", "LIQUOR", "JUGS",
    "THE", "QUICK", "BROWN", "FOX", "JUMPS", "OVER", "LAZY", "DOG",
    "SPHINX", "OF", "BLACK", "QUARTZ", "JUDGE", "MY", "VOW",
    "0123456789", "100", "250", "500", "999", "2026", "37", "88", "66",
]


def run_cmd(cmd, check=True):
    print(f"+ {' '.join(str(c) for c in cmd)}")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if check and res.returncode != 0:
        print(res.stdout)
        raise RuntimeError(f"Command failed with exit code {res.returncode}: {' '.join(str(c) for c in cmd)}")
    return res.stdout


def download_file(url: str, dest: Path):
    if dest.exists() and dest.stat().st_size > 1000:
        return
    print(f"Downloading {url} -> {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as f:
        shutil.copyfileobj(resp, f)


def prepare_directories():
    if WORK_DIR.exists():
        shutil.rmtree(WORK_DIR)
    for d in [FONTS_DIR, LANGDATA_DIR, GT_DIR, OUTPUT_DIR, FINAL_MODEL_PATH.parent]:
        d.mkdir(parents=True, exist_ok=True)


def generate_training_lines(num_lines: int = 320) -> list[str]:
    random.seed(42)
    lines = []
    # 1. Explicit alphabet & digits coverage lines
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    digits = "0123456789"
    for _ in range(15):
        shuffled_alpha = "".join(random.sample(alphabet, len(alphabet)))
        lines.append(f"{shuffled_alpha[:13]} {shuffled_alpha[13:]}")
        lines.append(f"{digits} {''.join(random.sample(digits, len(digits)))}")

    # 2. Iconic phrases & random 2-to-5 word combinations
    fixed_phrases = [
        "MAY THE FORCE BE WITH YOU",
        "WELCOME TO GALAXYS EDGE",
        "DOCKING BAY 94",
        "SECTOR 7G",
        "AUTHORIZED PERSONNEL ONLY",
        "DANGER BLAST DOORS",
        "THE QUICK BROWN FOX JUMPS",
        "OVER THE LAZY DOG 0123456789",
        "STAR WARS AUREBESH OCR",
        "IMPERIAL CARGO HOLD 42",
        "CANTINA CREDITS ONLY",
    ]
    for phrase in fixed_phrases * 4:
        lines.append(phrase)

    while len(lines) < num_lines:
        word_count = random.randint(2, 4)
        chosen = [random.choice(CORPUS_WORDS) for _ in range(word_count)]
        lines.append(" ".join(chosen))

    return lines[:num_lines]


def render_line_image(text: str, font_path: Path, font_size: int) -> Image.Image:
    font = ImageFont.truetype(str(font_path), font_size)
    dummy = Image.new("L", (10, 10), 255)
    draw = ImageDraw.Draw(dummy)
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w = max(1, bbox[2] - bbox[0])
    text_h = max(1, bbox[3] - bbox[1])

    pad_x = random.randint(24, 40)
    pad_y = random.randint(18, 28)
    img_w = text_w + pad_x * 2
    img_h = text_h + pad_y * 2

    img = Image.new("L", (img_w, img_h), 255)
    draw = ImageDraw.Draw(img)
    draw.text((pad_x - bbox[0], pad_y - bbox[1]), text, fill=0, font=font)

    # Légères augmentations réalistes (flou très léger + seuillage type Otsu)
    if random.random() < 0.35:
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 0.7)))
        threshold = random.randint(110, 160)
        img = img.point(lambda p: 255 if p > threshold else 0)

    return img


def generate_dataset(fonts: list[Path]):
    lines = generate_training_lines(500)
    lstmf_paths = []
    print(f"Generating {len(lines)} synthetic Aurebesh training samples across {len(fonts)} fonts...")

    for idx, text in enumerate(lines):
        font_path = fonts[idx % len(fonts)]
        font_size = random.choice([36, 42, 48, 54])
        # AurebeshAF-Canon utilise les glyphes canoniques en MAJUSCULES (sans ligatures),
        # tandis que les polices PixelSagas (Aurebesh.ttf, Bold, Condensed) ont les glyphes
        # canoniques non-inversés en minuscules.
        if "Canon" in font_path.name:
            render_text = text.upper()
        else:
            render_text = text.lower()
        img = render_line_image(render_text, font_path, font_size)
        base_name = f"aur_{idx:04d}"
        tif_path = GT_DIR / f"{base_name}.tif"
        box_path = GT_DIR / f"{base_name}.box"
        gt_txt_path = GT_DIR / f"{base_name}.gt.txt"

        img.save(tif_path, format="TIFF", dpi=(300, 300))
        gt_txt_path.write_text(text + "\n", encoding="utf-8")

        w, h = img.size
        box_content = f"WordStr 0 0 {w} {h} 0 #{text}\n\t 0 0 {w} {h} 0\n"
        box_path.write_text(box_content, encoding="utf-8")

        run_cmd(["tesseract", str(tif_path), str(GT_DIR / base_name), "--psm", "7", "lstm.train"])
        lstmf_file = GT_DIR / f"{base_name}.lstmf"
        if lstmf_file.exists():
            lstmf_paths.append(str(lstmf_file))

    train_list_path = WORK_DIR / "all-lstmf"
    train_list_path.write_text("\n".join(lstmf_paths) + "\n", encoding="utf-8")
    print(f"Generated {len(lstmf_paths)} .lstmf files.")
    return train_list_path, lines


def filter_compatible_fonts(downloaded_fonts: list[Path]) -> list[Path]:
    """Vérifie que chaque police rend bien des glyphes non vides pour A-Z et 0-9."""
    valid_fonts = []
    for fp in downloaded_fonts:
        try:
            font = ImageFont.truetype(str(fp), 48)
            img = Image.new("L", (400, 100), 255)
            draw = ImageDraw.Draw(img)
            draw.text((10, 10), "ABC123", fill=0, font=font)
            extrema = img.getextrema()
            if extrema[0] < 128:
                valid_fonts.append(fp)
                print(f"Font validated: {fp.name}")
        except Exception as e:
            print(f"Skipping font {fp}: {e}")
    return valid_fonts


def build_and_train():
    prepare_directories()

    downloaded_fonts = []
    for name, url in FONT_URLS.items():
        dest = FONTS_DIR / name
        try:
            download_file(url, dest)
            downloaded_fonts.append(dest)
        except Exception as e:
            print(f"Warning: could not download {name}: {e}")

    valid_fonts = filter_compatible_fonts(downloaded_fonts)
    if not valid_fonts:
        raise RuntimeError("No valid Aurebesh fonts downloaded!")

    eng_traineddata = WORK_DIR / "eng.traineddata"
    download_file(TESSDATA_BEST_ENG_URL, eng_traineddata)

    radical_stroke = LANGDATA_DIR / "radical-stroke.txt"
    download_file(RADICAL_STROKE_URL, radical_stroke)

    train_list_path, lines = generate_dataset(valid_fonts)

    # Extraction de l'unicharset à partir des fichiers .box
    box_files = [str(p) for p in sorted(GT_DIR.glob("*.box"))]
    unicharset_path = WORK_DIR / "unicharset"
    run_cmd(["unicharset_extractor", "--output_unicharset", str(unicharset_path), "--norm_mode", "1"] + box_files)

    # Création du starter traineddata pour "aurebesh"
    aur_lang_dir = LANGDATA_DIR / "aurebesh"
    aur_lang_dir.mkdir(parents=True, exist_ok=True)
    (aur_lang_dir / "aurebesh.training_text").write_text("\n".join(lines) + "\n", encoding="utf-8")

    starter_dir = WORK_DIR / "starter"
    starter_dir.mkdir(parents=True, exist_ok=True)
    run_cmd([
        "combine_lang_model",
        "--input_unicharset", str(unicharset_path),
        "--script_dir", str(LANGDATA_DIR),
        "--output_dir", str(starter_dir),
        "--lang", "aurebesh",
    ])
    starter_traineddata = starter_dir / "aurebesh" / "aurebesh.traineddata"

    # Extraction du modèle LSTM de base (eng) pour transfert des couches basses et remplacement de la couche finale
    eng_lstm = WORK_DIR / "eng.lstm"
    run_cmd(["combine_tessdata", "-e", str(eng_traineddata), str(eng_lstm)])

    checkpoint_prefix = OUTPUT_DIR / "aurebesh"
    print("Starting LSTM training (transfer learning from eng with replaced top layer)...")
    run_cmd([
        "lstmtraining",
        "--continue_from", str(eng_lstm),
        "--old_traineddata", str(eng_traineddata),
        "--traineddata", str(starter_traineddata),
        "--append_index", "5",
        "--net_spec", "[Lfx192 O1c1]",
        "--model_output", str(checkpoint_prefix),
        "--train_listfile", str(train_list_path),
        "--max_iterations", "5000",
        "--target_error_rate", "0.0001",
    ])

    checkpoint_file = Path(f"{checkpoint_prefix}_checkpoint")
    if not checkpoint_file.exists():
        raise RuntimeError("Training did not produce a checkpoint!")

    # Export du modèle final quantifié (int) optimisé pour mobile Android
    run_cmd([
        "lstmtraining",
        "--stop_training",
        "--convert_to_int",
        "--continue_from", str(checkpoint_file),
        "--traineddata", str(starter_traineddata),
        "--model_output", str(FINAL_MODEL_PATH),
    ])

    print(f"\nModel successfully generated: {FINAL_MODEL_PATH} ({FINAL_MODEL_PATH.stat().st_size / 1024:.1f} KB)")

    # Test de validation sur des phrases Aurebesh inédites sur toutes les polices
    for vf in valid_fonts:
        print(f"\n=== Test avec la police : {vf.name} ===")
        verify_model(vf)


def verify_model(test_font: Path):
    print("\n--- Validation du modèle `aurebesh.traineddata` ---")
    tessdata_test_dir = WORK_DIR / "tessdata_test"
    tessdata_test_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(FINAL_MODEL_PATH, tessdata_test_dir / "aurebesh.traineddata")

    test_phrases = [
        "MAY THE FORCE BE WITH YOU",
        "WELCOME TO GALAXYS EDGE",
        "DOCKING BAY 94",
        "SECTOR 7G",
    ]
    for phrase in test_phrases:
        render_phrase = phrase.upper() if "Canon" in test_font.name else phrase.lower()
        img = render_line_image(render_phrase, test_font, 48)
        test_img_path = WORK_DIR / "test_sample.png"
        img.save(test_img_path)
        out = run_cmd([
            "tesseract",
            str(test_img_path),
            "stdout",
            "--tessdata-dir", str(tessdata_test_dir),
            "-l", "aurebesh",
            "--psm", "7",
        ])
        recognized = out.strip()
        print(f"Expected : '{phrase}' | Recognized : '{recognized}'")


if __name__ == "__main__":
    build_and_train()
