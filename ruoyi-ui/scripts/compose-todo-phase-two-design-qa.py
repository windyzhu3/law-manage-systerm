from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
REFERENCE = (
    REPOSITORY_ROOT
    / "docs"
    / "superpowers"
    / "specs"
    / "assets"
    / "2026-07-23-todo-engine-phase-two-ux-selected.png"
)
OUTPUT_ROOT = REPOSITORY_ROOT / "output" / "playwright" / "todo-phase-two-ux"
VIEWPORTS = ("1440x1024", "1920x1080")
PANEL_WIDTH = 1600
PANEL_HEIGHT = 900
LABEL_HEIGHT = 60
PADDING = 24
GAP = 24


def require_image(path: Path) -> Image.Image:
    if not path.is_file():
        raise FileNotFoundError(f"required design QA image is missing: {path}")
    image = Image.open(path).convert("RGB")
    if image.width <= 0 or image.height <= 0:
        raise ValueError(f"required design QA image has zero dimensions: {path}")
    return image


def fit(image: Image.Image) -> Image.Image:
    scale = min(PANEL_WIDTH / image.width, PANEL_HEIGHT / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    return image.resize((width, height), Image.Resampling.LANCZOS)


def font(size: int) -> ImageFont.ImageFont:
    for candidate in (
        Path("C:/Windows/Fonts/segoeuib.ttf"),
        Path("C:/Windows/Fonts/arialbd.ttf"),
    ):
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size=size)
    return ImageFont.load_default()


def paste_centered(canvas: Image.Image, image: Image.Image, x: int, y: int) -> None:
    canvas.paste(
        image,
        (
            x + (PANEL_WIDTH - image.width) // 2,
            y + (PANEL_HEIGHT - image.height) // 2,
        ),
    )


def compose(viewport: str) -> Path:
    reference = fit(require_image(REFERENCE))
    implementation_path = OUTPUT_ROOT / f"{viewport}-implementation.png"
    implementation = fit(require_image(implementation_path))
    width = PADDING * 2 + PANEL_WIDTH * 2 + GAP
    height = PADDING * 2 + LABEL_HEIGHT + PANEL_HEIGHT
    canvas = Image.new("RGB", (width, height), "#F4F7FA")
    draw = ImageDraw.Draw(canvas)
    label_font = font(28)
    left_x = PADDING
    right_x = PADDING + PANEL_WIDTH + GAP
    draw.rounded_rectangle(
        (left_x, PADDING, left_x + PANEL_WIDTH, height - PADDING),
        radius=12,
        fill="#FFFFFF",
        outline="#D9E1EA",
        width=2,
    )
    draw.rounded_rectangle(
        (right_x, PADDING, right_x + PANEL_WIDTH, height - PADDING),
        radius=12,
        fill="#FFFFFF",
        outline="#D9E1EA",
        width=2,
    )
    draw.text((left_x + 20, PADDING + 13), "Approved reference", fill="#0B2A55", font=label_font)
    draw.text(
        (right_x + 20, PADDING + 13),
        f"Implementation · {viewport}",
        fill="#0B2A55",
        font=label_font,
    )
    image_y = PADDING + LABEL_HEIGHT
    paste_centered(canvas, reference, left_x, image_y)
    paste_centered(canvas, implementation, right_x, image_y)
    output = OUTPUT_ROOT / f"{viewport}-comparison.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output, "PNG", optimize=True)
    if output.stat().st_size <= 0:
        raise ValueError(f"design QA comparison is empty: {output}")
    return output


def main() -> None:
    for viewport in VIEWPORTS:
        output = compose(viewport)
        print(f"created {output}")


if __name__ == "__main__":
    main()
