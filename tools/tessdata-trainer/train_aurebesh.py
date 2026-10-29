#!/usr/bin/env python3
"""
Script d'entraînement sur mesure du modèle Tesseract `aurebesh.traineddata`
pour l'application Android OCRAurebesh, avec augmentations avancées pour caméra mobile :
- Polices Aurebesh multiples (Regular, Bold, Italic, Condensed, Canon AF, Cantina, Droid, Droid Depot)
- Mode plein (solid) ET mode contour / outline (intérieur de la lettre de la même couleur que le fond)
- Petites tailles de police (lignes denses de 25 à 35 caractères, paragraphes multi-lignes)
- Flou optique marqué (mise au point imparfaite) + rehaussement / seuillage varié
- Casse mixte (minuscules standard, majuscules Aurebesh inversées et Title Case)
"""
import concurrent.futures
import os
import random
import shutil
import subprocess
import urllib.request
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

WORK_DIR = Path("/tmp/aurebesh_build")
FONTS_DIR = WORK_DIR / "fonts"
LOCAL_ASSETS_FONTS_DIR = Path("/workspace/app/src/main/assets/fonts")
LANGDATA_DIR = WORK_DIR / "langdata"
GT_DIR = WORK_DIR / "gt"
OUTPUT_DIR = WORK_DIR / "output"
FINAL_MODEL_PATH = Path("/workspace/output/aurebesh.traineddata")

FONT_URLS = {
    "aurebesh_regular.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh.ttf",
    "aurebesh_bold.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh%20Bold.ttf",
    "aurebesh_condensed.ttf": "https://raw.githubusercontent.com/Taminoful/aurebesh-font-pipeline/main/src/Aurebesh%20Condensed.ttf",
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
    "EDGE", "ORDER", "RADAR", "UNDER", "GUARD", "DARK",
    "DOOR", "RED", "RIDGE", "YOUR", "YOUNG", "YODA", "YAVIN",
    "PACK", "MY", "BOX", "FIVE", "DOZEN", "LIQUOR", "JUGS",
    "QUICK", "BROWN", "FOX", "JUMPS", "OVER", "LAZY", "DOG",
    "SPHINX", "OF", "BLACK", "QUARTZ", "JUDGE", "VOW",
    "HELLO", "WORLD", "WELCOME", "SYSTEM", "ONLINE", "OFFLINE", "LOCKED",
    "OPEN", "CLOSED", "WARNING", "ACCESS", "DENIED", "GRANTED", "TERMINAL",
    "BONJOUR", "SALUT", "TEXTE", "MESSAGE", "SECRET", "MISSION", "PLANETE",
    "VAISSEAU", "PILOTE", "ESCADRON", "BASE", "COMMANDEMENT", "TRANSMISSION",
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


def generate_training_lines(num_lines: int = 1350) -> list[str]:
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
    ] * 10)

    for _ in range(45):
        shuffled_alpha = "".join(random.sample(alphabet, len(alphabet)))
        lines.append(f"{shuffled_alpha[:9]} {shuffled_alpha[9:18]} {shuffled_alpha[18:]}")
        lines.append(f"{digits} {''.join(random.sample(digits, len(digits)))}")

    # 2. Mots uniques (panneaux, portes, boutons d'interface Star Wars)
    for word in CORPUS_WORDS:
        lines.append(word)

    # 3. Phrases iconiques & lignes longues (~30 caractères) pour les paragraphes denses
    fixed_phrases = [
        "MAY THE FORCE BE WITH YOU",
        "MAY THE FORCE BE WITH YOU ALWAYS",
        "WELCOME TO GALAXYS EDGE",
        "DOCKING BAY 94 SECTOR 7G",
        "AUTHORIZED PERSONNEL ONLY",
        "DANGER BLAST DOORS CLOSED",
        "THE QUICK BROWN FOX JUMPS OVER",
        "THE LAZY DOG 0123456789",
        "STAR WARS AUREBESH OCR STUDIO",
        "IMPERIAL CARGO HOLD LEVEL 1313",
        "CANTINA CREDITS ONLY NO DROIDS",
        "ACCESS GRANTED TERMINAL ONLINE",
        "ACCESS DENIED SHIELD GENERATOR",
        "TRANSMISSION SECRET REBEL BASE",
    ]
    for phrase in fixed_phrases * 6:
        lines.append(phrase)

    while len(lines) < num_lines:
        word_count = random.choices([1, 2, 3, 4, 5, 6], weights=[12, 22, 24, 20, 14, 8])[0]
        chosen = [random.choice(CORPUS_WORDS) for _ in range(word_count)]
        candidate = " ".join(chosen)
        # On garde des lignes jusqu'à ~36 caractères pour entraîner sur les lignes de 30 caractères
        if len(candidate) > 38:
            candidate = candidate[:38].rsplit(" ", 1)[0]
        lines.append(candidate)

    random.shuffle(lines)
    return lines[:num_lines]


def draw_text_with_tracking(
    draw: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    text: str,
    font: ImageFont.FreeTypeFont,
    tracking: int,
    fill: int = 0,
    stroke_width: int = 0,
    stroke_fill: int = 0,
):
    x, y = xy
    for ch in text:
        draw.text(
            (x, y),
            ch,
            fill=fill,
            font=font,
            stroke_width=stroke_width,
            stroke_fill=stroke_fill,
        )
        bbox = draw.textbbox((0, 0), ch, font=font, stroke_width=stroke_width)
        adv = max(font.size // 3, bbox[2] - bbox[0]) if ch == " " else (bbox[2] - bbox[0])
        x += adv + tracking


def measure_text_with_tracking(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
    tracking: int,
    stroke_width: int = 0,
) -> tuple[int, int]:
    total_w = 0
    max_h = font.size
    for idx, ch in enumerate(text):
        bbox = draw.textbbox((0, 0), ch, font=font, stroke_width=stroke_width)
        adv = max(font.size // 3, bbox[2] - bbox[0]) if ch == " " else (bbox[2] - bbox[0])
        total_w += adv + (tracking if idx < len(text) - 1 else 0)
        max_h = max(max_h, bbox[3] - bbox[1] + 8)
    return max(1, total_w), max(1, max_h)


def render_line_image(
    text: str,
    font_path: Path,
    font_size: int,
    augment: bool = True,
    outline_mode: str = "solid",
) -> Image.Image:
    """
    Rend une ligne de texte Aurebesh avec support :
    - `outline_mode="solid"` : glyphes pleins classiques (noir sur fond blanc).
    - `outline_mode="stroke"` : glyphes en mode contour (intérieur blanc identique au fond, contour noir).
    - `outline_mode="morph_edge"` : glyphes évidés par gradient morphologique (contour fin).
    """
    font = ImageFont.truetype(str(font_path), font_size)
    dummy = Image.new("L", (10, 10), 255)
    draw = ImageDraw.Draw(dummy)

    stroke_w = 0
    if outline_mode == "stroke":
        stroke_w = 1 if font_size < 34 else random.choice([1, 2])

    tracking = random.choice([0, 0, 0, 1, 2, 3]) if augment else 0
    if outline_mode == "stroke" and tracking == 0:
        tracking = stroke_w * 2

    if tracking == 0:
        bbox = draw.textbbox((0, 0), text, font=font, stroke_width=stroke_w)
        text_w = max(1, bbox[2] - bbox[0])
        text_h = max(1, bbox[3] - bbox[1])
        offset_x = -bbox[0]
        offset_y = -bbox[1]
    else:
        text_w, text_h = measure_text_with_tracking(draw, text, font, tracking, stroke_width=stroke_w)
        offset_x = 0
        offset_y = 0

    pad_x = random.randint(16, 34) if augment else 24
    pad_y = random.randint(12, 24) if augment else 18
    img_w = text_w + pad_x * 2
    img_h = text_h + pad_y * 2

    img = Image.new("L", (img_w, img_h), 255)
    draw = ImageDraw.Draw(img)

    fill_color = 255 if outline_mode == "stroke" else 0
    stroke_fill_color = 0

    if tracking == 0:
        draw.text(
            (pad_x + offset_x, pad_y + offset_y),
            text,
            fill=fill_color,
            font=font,
            stroke_width=stroke_w,
            stroke_fill=stroke_fill_color,
        )
    else:
        draw_text_with_tracking(
            draw,
            (pad_x, pad_y),
            text,
            font,
            tracking,
            fill=fill_color,
            stroke_width=stroke_w,
            stroke_fill=stroke_fill_color,
        )

    if outline_mode == "morph_edge":
        # Génère un contour creux (intérieur blanc = couleur du fond, bordure noire)
        dilated = img.filter(ImageFilter.MaxFilter(3))
        eroded = img.filter(ImageFilter.MinFilter(3))
        edge = ImageChops.subtract(dilated, eroded)
        img = ImageOps.invert(edge)
        img = img.point(lambda p: 255 if p > 180 else 0)

    if not augment:
        return img

    # 1. Légère inclinaison aléatoire compatible avec une boîte WordStr 1D (-0.8° à +0.8°)
    if random.random() < 0.45:
        angle = random.uniform(-0.8, 0.8)
        img = img.rotate(angle, resample=Image.BICUBIC, expand=False, fillcolor=255)

    # 2. Simulation de la mise au point caméra (flou léger à fort) + seuillage
    aug_mode = random.random()
    if outline_mode != "solid":
        # Pour les lettres en mode contour, simulation de flou + rehaussement / seuillage adaptatif
        if aug_mode < 0.50:
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 0.9)))
            threshold = random.randint(135, 195)
            img = img.point(lambda p: 255 if p > threshold else 0)
        elif aug_mode < 0.80:
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.8, 1.5)))
            img = img.filter(ImageFilter.UnsharpMask(radius=1.5, percent=180, threshold=2))
            threshold = random.randint(140, 190)
            img = img.point(lambda p: 255 if p > threshold else 0)
    else:
        if aug_mode < 0.25:
            # Flou léger + seuillage bas -> traits plus fins (halo lumineux fort)
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.4, 1.1)))
            threshold = random.randint(75, 115)
            img = img.point(lambda p: 255 if p > threshold else 0)
        elif aug_mode < 0.55:
            # Flou léger + seuillage haut -> traits plus gras (sous-exposition ou encre épaisse)
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.4, 1.2)))
            threshold = random.randint(145, 195)
            img = img.point(lambda p: 255 if p > threshold else 0)
        elif aug_mode < 0.82:
            # Flou optique marqué (caméra mal mise au point ou petit texte à distance) + UnsharpMask
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(1.1, 2.0)))
            img = img.filter(ImageFilter.UnsharpMask(radius=1.6, percent=170, threshold=3))
            threshold = random.randint(110, 155)
            img = img.point(lambda p: 255 if p > threshold else 0)
        else:
            # Binarisation standard avec flou optique modéré
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 0.8)))
            threshold = random.randint(115, 145)
            img = img.point(lambda p: 255 if p > threshold else 0)

    return img


def load_available_fonts() -> list[Path]:
    """
    Charge les polices Aurebesh depuis `app/src/main/assets/fonts/` (monté dans `/workspace`)
    avec repli sur le téléchargement GitHub si nécessaire.
    """
    preferred_assets = [
        "aurebesh_regular.ttf",
        "aurebesh_af_canon.otf",
        "aurebesh_bold.ttf",
        "aurebesh_condensed.ttf",
    ]
    collected = []
    if LOCAL_ASSETS_FONTS_DIR.exists():
        for name in preferred_assets:
            src = LOCAL_ASSETS_FONTS_DIR / name
            if src.exists():
                dst = FONTS_DIR / name
                if not dst.exists():
                    shutil.copy2(src, dst)
                collected.append(dst)

    if not collected:
        for name, url in FONT_URLS.items():
            dest = FONTS_DIR / name
            try:
                download_file(url, dest)
                collected.append(dest)
            except Exception as e:
                print(f"Warning: could not download {name}: {e}")

    return collected


def choose_render_case(gt_text: str, font_name: str, idx: int) -> str:
    if "canon" in font_name.lower():
        return gt_text.upper()
    return gt_text.lower()


def _build_single_sample(args):
    idx, text, font_path, font_size, render_text, outline_mode, augment = args
    img = render_line_image(
        render_text,
        font_path,
        font_size,
        augment=augment,
        outline_mode=outline_mode,
    )
    base_name = f"aur_{idx:04d}"
    tif_path = GT_DIR / f"{base_name}.tif"
    box_path = GT_DIR / f"{base_name}.box"
    gt_txt_path = GT_DIR / f"{base_name}.gt.txt"

    img.save(tif_path, format="TIFF", dpi=(300, 300))
    gt_txt_path.write_text(text + "\n", encoding="utf-8")

    w, h = img.size
    box_content = f"WordStr 0 0 {w} {h} 0 #{text}\n\t 0 0 {w} {h} 0\n"
    box_path.write_text(box_content, encoding="utf-8")

    subprocess.run(
        ["tesseract", str(tif_path), str(GT_DIR / base_name), "--psm", "6", "lstm.train"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=True,
    )
    lstmf_file = GT_DIR / f"{base_name}.lstmf"
    return str(lstmf_file) if lstmf_file.exists() else None


def generate_dataset(fonts: list[Path]):
    lines = generate_training_lines(900)
    print(f"Generating {len(lines)} camera-augmented Aurebesh training samples in parallel...")

    tasks = []
    for idx, text in enumerate(lines):
        font_path = fonts[idx % len(fonts)]
        font_size = random.choice([20, 24, 28, 32, 36, 42, 48, 54])
        render_text = choose_render_case(text, font_path.name, idx)

        mode_roll = idx % 10
        if mode_roll in (7, 8):
            outline_mode = "stroke"
            font_size = max(26, font_size)
        elif mode_roll == 9:
            outline_mode = "morph_edge"
            font_size = max(26, font_size)
        else:
            outline_mode = "solid"

        augment = (idx % 7 != 0)
        tasks.append((idx, text, font_path, font_size, render_text, outline_mode, augment))

    workers = max(4, (os.cpu_count() or 8))
    lstmf_paths = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        for res in pool.map(_build_single_sample, tasks):
            if res:
                lstmf_paths.append(res)

    train_list_path = WORK_DIR / "all-lstmf"
    train_list_path.write_text("\n".join(lstmf_paths) + "\n", encoding="utf-8")
    print(f"Generated {len(lstmf_paths)} .lstmf files using {workers} workers.")
    return train_list_path, lines


def filter_compatible_fonts(candidate_fonts: list[Path]) -> list[Path]:
    valid_fonts = []
    for fp in candidate_fonts:
        try:
            font = ImageFont.truetype(str(fp), 48)
            img = Image.new("L", (400, 100), 255)
            draw = ImageDraw.Draw(img)
            draw.text((10, 10), "ABCabc123", fill=0, font=font)
            extrema = img.getextrema()
            if extrema[0] < 128:
                valid_fonts.append(fp)
                print(f"Font validated: {fp.name}")
        except Exception as e:
            print(f"Skipping font {fp}: {e}")
    return valid_fonts


def build_and_train():
    prepare_directories()

    candidate_fonts = load_available_fonts()
    valid_fonts = filter_compatible_fonts(candidate_fonts)
    if not valid_fonts:
        raise RuntimeError("No valid Aurebesh fonts available!")

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
    print("Starting LSTM training (transfer learning from eng with solid + contour + blur + multi-font augmentations)...")
    run_cmd([
        "lstmtraining",
        "--continue_from", str(eng_lstm),
        "--old_traineddata", str(eng_traineddata),
        "--traineddata", str(starter_traineddata),
        "--append_index", "5",
        "--net_spec", "[Lfx192 O1c1]",
        "--model_output", str(checkpoint_prefix),
        "--train_listfile", str(train_list_path),
        "--max_iterations", "7000",
        "--target_error_rate", "0.0003",
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

    seen = set()
    for vf in valid_fonts:
        if vf.name not in seen:
            seen.add(vf.name)
            print(f"\n=== Test avec la police : {vf.name} ===")
            verify_model(vf)


def verify_model(test_font: Path):
    print("\n--- Validation du modèle `aurebesh.traineddata` (Solid + Contour + Petit texte) ---")
    tessdata_test_dir = WORK_DIR / "tessdata_test"
    tessdata_test_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(FINAL_MODEL_PATH, tessdata_test_dir / "aurebesh.traineddata")

    test_cases = [
        ("MAY THE FORCE BE WITH YOU", "solid", 42),
        ("WELCOME TO GALAXYS EDGE", "stroke", 44),
        ("DOCKING BAY 94 SECTOR 7G", "morph_edge", 44),
        ("AUTHORIZED PERSONNEL ONLY", "solid", 24),
    ]
    for phrase, mode, fsize in test_cases:
        render_phrase = phrase.lower() if "canon" not in test_font.name.lower() else phrase.upper()
        img = render_line_image(render_phrase, test_font, fsize, augment=True, outline_mode=mode)
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
        print(f"[{mode} @ {fsize}px] Expected : '{phrase}' | Recognized : '{recognized}'")


if __name__ == "__main__":
    build_and_train()
