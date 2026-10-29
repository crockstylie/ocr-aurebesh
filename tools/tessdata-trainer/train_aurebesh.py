#!/usr/bin/env python3
"""
Script d'entraînement sur mesure du modèle Tesseract `aurebesh.traineddata`
pour l'application Android OCRAurebesh, avec augmentations réalistes pour caméra mobile
(inclinaison, halo d'écran / érosion-dilatation des traits, flou optique, espacement variable).
"""
import random
import shutil
import subprocess
import urllib.request
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
    "HELLO", "WORLD", "WELCOME", "SYSTEM", "ONLINE", "OFFLINE", "LOCKED",
    "OPEN", "CLOSED", "WARNING", "ACCESS", "DENIED", "GRANTED", "TERMINAL",
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


def generate_training_lines(num_lines: int = 900) -> list[str]:
    random.seed(42)
    lines = []
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    digits = "0123456789"

    # 1. Couverture explicite de l'alphabet (séquentiel, par blocs et mélangé)
    lines.extend([
        "ABCDEFG HIJKLMN OPQRST UVWXYZ",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        "ABCDEFGHIJKLM NOPQRSTUVWXYZ",
        "0123456789 9876543210",
    ] * 8)

    for _ in range(30):
        shuffled_alpha = "".join(random.sample(alphabet, len(alphabet)))
        lines.append(f"{shuffled_alpha[:9]} {shuffled_alpha[9:18]} {shuffled_alpha[18:]}")
        lines.append(f"{digits} {''.join(random.sample(digits, len(digits)))}")

    # 2. Mots uniques (panneaux, portes, boutons d'interface Star Wars)
    for word in CORPUS_WORDS:
        lines.append(word)

    # 3. Phrases iconiques & combinaisons de 1 à 4 mots
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
        "ACCESS GRANTED",
        "ACCESS DENIED",
        "SYSTEM ONLINE",
    ]
    for phrase in fixed_phrases * 5:
        lines.append(phrase)

    while len(lines) < num_lines:
        word_count = random.choices([1, 2, 3, 4], weights=[20, 35, 30, 15])[0]
        chosen = [random.choice(CORPUS_WORDS) for _ in range(word_count)]
        lines.append(" ".join(chosen))

    random.shuffle(lines)
    return lines[:num_lines]


def draw_text_with_tracking(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, font: ImageFont.FreeTypeFont, tracking: int):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, fill=0, font=font)
        bbox = draw.textbbox((0, 0), ch, font=font)
        adv = max(font.size // 3, bbox[2] - bbox[0]) if ch == " " else (bbox[2] - bbox[0])
        x += adv + tracking


def measure_text_with_tracking(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, tracking: int) -> tuple[int, int]:
    total_w = 0
    max_h = font.size
    for idx, ch in enumerate(text):
        bbox = draw.textbbox((0, 0), ch, font=font)
        adv = max(font.size // 3, bbox[2] - bbox[0]) if ch == " " else (bbox[2] - bbox[0])
        total_w += adv + (tracking if idx < len(text) - 1 else 0)
        max_h = max(max_h, bbox[3] - bbox[1] + 8)
    return max(1, total_w), max(1, max_h)


def render_line_image(text: str, font_path: Path, font_size: int, augment: bool = True) -> Image.Image:
    font = ImageFont.truetype(str(font_path), font_size)
    dummy = Image.new("L", (10, 10), 255)
    draw = ImageDraw.Draw(dummy)

    # Espacement inter-caractères variable (0 = normal, 1..4 = espacé type interface / affiche)
    tracking = random.choice([0, 0, 0, 1, 2, 4]) if augment else 0

    if tracking == 0:
        bbox = draw.textbbox((0, 0), text, font=font)
        text_w = max(1, bbox[2] - bbox[0])
        text_h = max(1, bbox[3] - bbox[1])
        offset_x = -bbox[0]
        offset_y = -bbox[1]
    else:
        text_w, text_h = measure_text_with_tracking(draw, text, font, tracking)
        offset_x = 0
        offset_y = 0

    pad_x = random.randint(20, 40) if augment else 28
    pad_y = random.randint(14, 28) if augment else 20
    img_w = text_w + pad_x * 2
    img_h = text_h + pad_y * 2

    img = Image.new("L", (img_w, img_h), 255)
    draw = ImageDraw.Draw(img)

    if tracking == 0:
        draw.text((pad_x + offset_x, pad_y + offset_y), text, fill=0, font=font)
    else:
        draw_text_with_tracking(draw, (pad_x, pad_y), text, font, tracking)

    if not augment:
        return img

    # 1. Légère inclinaison aléatoire compatible avec une boîte WordStr 1D (-0.8° à +0.8°)
    if random.random() < 0.45:
        angle = random.uniform(-0.8, 0.8)
        img = img.rotate(angle, resample=Image.BICUBIC, expand=False, fillcolor=255)

    # 2. Simulation de la mise au point caméra + variation d'épaisseur des traits
    #    (halo lumineux sur écran sombre qui affine ou épaissit les traits après binarisation)
    aug_mode = random.random()
    if aug_mode < 0.30:
        # Flou léger + seuillage bas -> traits plus fins (halo lumineux fort)
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.4, 1.0)))
        threshold = random.randint(75, 115)
        img = img.point(lambda p: 255 if p > threshold else 0)
    elif aug_mode < 0.65:
        # Flou léger + seuillage haut -> traits plus gras (sous-exposition ou encre épaisse)
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.4, 1.1)))
        threshold = random.randint(145, 190)
        img = img.point(lambda p: 255 if p > threshold else 0)
    elif aug_mode < 0.85:
        # Binarisation standard avec flou optique modéré
        img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 0.8)))
        threshold = random.randint(115, 145)
        img = img.point(lambda p: 255 if p > threshold else 0)

    return img


def generate_dataset(fonts: list[Path]):
    lines = generate_training_lines(900)
    lstmf_paths = []
    print(f"Generating {len(lines)} camera-augmented Aurebesh training samples across {len(fonts)} fonts...")

    for idx, text in enumerate(lines):
        font_path = fonts[idx % len(fonts)]
        font_size = random.choice([32, 38, 44, 50, 58])
        render_text = text.upper() if "Canon" in font_path.name else text.lower()
        # Garder 20% d'échantillons propres et 80% d'échantillons augmentés caméra
        augment = (idx % 5 != 0)
        img = render_line_image(render_text, font_path, font_size, augment=augment)
        base_name = f"aur_{idx:04d}"
        tif_path = GT_DIR / f"{base_name}.tif"
        box_path = GT_DIR / f"{base_name}.box"
        gt_txt_path = GT_DIR / f"{base_name}.gt.txt"

        img.save(tif_path, format="TIFF", dpi=(300, 300))
        gt_txt_path.write_text(text + "\n", encoding="utf-8")

        w, h = img.size
        box_content = f"WordStr 0 0 {w} {h} 0 #{text}\n\t 0 0 {w} {h} 0\n"
        box_path.write_text(box_content, encoding="utf-8")

        run_cmd(["tesseract", str(tif_path), str(GT_DIR / base_name), "--psm", "6", "lstm.train"])
        lstmf_file = GT_DIR / f"{base_name}.lstmf"
        if lstmf_file.exists():
            lstmf_paths.append(str(lstmf_file))

    train_list_path = WORK_DIR / "all-lstmf"
    train_list_path.write_text("\n".join(lstmf_paths) + "\n", encoding="utf-8")
    print(f"Generated {len(lstmf_paths)} .lstmf files.")
    return train_list_path, lines


def filter_compatible_fonts(downloaded_fonts: list[Path]) -> list[Path]:
    valid_fonts = []
    for fp in downloaded_fonts:
        try:
            font = ImageFont.truetype(str(fp), 48)
            img = Image.new("L", (400, 100), 255)
            draw = ImageDraw.Draw(img)
            draw.text((10, 10), "abc123", fill=0, font=font)
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

    box_files = [str(p) for p in sorted(GT_DIR.glob("*.box"))]
    unicharset_path = WORK_DIR / "unicharset"
    run_cmd(["unicharset_extractor", "--output_unicharset", str(unicharset_path), "--norm_mode", "1"] + box_files)

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

    eng_lstm = WORK_DIR / "eng.lstm"
    run_cmd(["combine_tessdata", "-e", str(eng_traineddata), str(eng_lstm)])

    checkpoint_prefix = OUTPUT_DIR / "aurebesh"
    print("Starting LSTM training (transfer learning from eng with camera augmentations)...")
    run_cmd([
        "lstmtraining",
        "--continue_from", str(eng_lstm),
        "--old_traineddata", str(eng_traineddata),
        "--traineddata", str(starter_traineddata),
        "--append_index", "5",
        "--net_spec", "[Lfx192 O1c1]",
        "--model_output", str(checkpoint_prefix),
        "--train_listfile", str(train_list_path),
        "--max_iterations", "8000",
        "--target_error_rate", "0.0002",
    ])

    checkpoint_file = Path(f"{checkpoint_prefix}_checkpoint")
    if not checkpoint_file.exists():
        raise RuntimeError("Training did not produce a checkpoint!")

    run_cmd([
        "lstmtraining",
        "--stop_training",
        "--convert_to_int",
        "--continue_from", str(checkpoint_file),
        "--traineddata", str(starter_traineddata),
        "--model_output", str(FINAL_MODEL_PATH),
    ])

    print(f"\nModel successfully generated: {FINAL_MODEL_PATH} ({FINAL_MODEL_PATH.stat().st_size / 1024:.1f} KB)")

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
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    ]
    for phrase in test_phrases:
        render_phrase = phrase.upper() if "Canon" in test_font.name else phrase.lower()
        img = render_line_image(render_phrase, test_font, 48, augment=True)
        test_img_path = WORK_DIR / "test_sample.png"
        img.save(test_img_path)
        out = run_cmd([
            "tesseract",
            str(test_img_path),
            "stdout",
            "--tessdata-dir", str(tessdata_test_dir),
            "-l", "aurebesh",
            "--psm", "6",
        ])
        recognized = out.strip()
        print(f"Expected : '{phrase}' | Recognized : '{recognized}'")


if __name__ == "__main__":
    build_and_train()
