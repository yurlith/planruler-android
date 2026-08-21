from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "play-store"
OUT.mkdir(parents=True, exist_ok=True)

FONT_REGULAR = Path(r"C:\Windows\Fonts\segoeui.ttf")
FONT_SEMIBOLD = Path(r"C:\Windows\Fonts\seguisb.ttf")
FONT_BOLD = Path(r"C:\Windows\Fonts\segoeuib.ttf")


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size=size)


def draw_planruler_mark(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    width = x1 - x0
    scale = width / 108.0

    def point(x: float, y: float) -> tuple[float, float]:
        return x0 + x * scale, y0 + y * scale

    light = "#F5F7FA"
    cyan = "#35C6E8"
    orange = "#FF8A35"
    stroke = max(2, round(7 * scale))

    # The store mark mirrors the Android adaptive icon: a P-shaped plan line,
    # a compact ruler and one orange datum block.
    draw.line([point(31, 84), point(31, 25), point(63, 25)], fill=light, width=stroke, joint="curve")
    draw.arc([*point(44, 25), *point(86, 74)], start=270, end=90, fill=light, width=stroke)
    draw.line([point(31, 69), point(72, 69)], fill=cyan, width=max(2, round(3 * scale)))
    for x in (40, 51, 61, 72):
        tick = 9 if x in (40, 72) else 6
        draw.line([point(x, 69 - tick / 2), point(x, 69 + tick / 2)], fill=cyan, width=max(2, round(3 * scale)))
    draw.rounded_rectangle([*point(31, 79), *point(43, 88)], radius=max(1, round(2 * scale)), fill=orange)


def create_icon() -> Path:
    size = 512
    image = Image.new("RGB", (size, size), "#10233F")
    draw = ImageDraw.Draw(image)
    # Subtle blueprint grid remains inside the artwork and survives Play masking.
    for p in range(64, size, 64):
        draw.line((p, 0, p, size), fill="#173052", width=1)
        draw.line((0, p, size, p), fill="#173052", width=1)
    draw.rounded_rectangle((42, 42, 470, 470), radius=118, fill="#16325C")
    draw_planruler_mark(draw, (88, 88, 424, 424))
    target = OUT / "app-icon-512.png"
    image.save(target, "PNG", optimize=True)
    return target


def create_feature_graphic() -> Path:
    width, height = 1024, 500
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            t = (x / width) * 0.72 + (y / height) * 0.28
            pixels[x, y] = (
                int(7 + 12 * t),
                int(21 + 25 * t),
                int(40 + 48 * t),
            )

    draw = ImageDraw.Draw(image)
    for p in range(-500, 1200, 56):
        draw.line((p, 0, p + 500, 500), fill="#17345A", width=1)
    draw.ellipse((760, -170, 1140, 210), fill="#123B67")
    draw.ellipse((830, 330, 1110, 610), fill="#0D504F")

    draw.rounded_rectangle((56, 68, 244, 256), radius=48, fill="#163A69")
    draw_planruler_mark(draw, (71, 83, 229, 241))
    draw.text((286, 82), "PlanRuler", font=font(FONT_BOLD, 78), fill="#F7F9FD")
    draw.text((291, 180), "Work centre for installers", font=font(FONT_SEMIBOLD, 31), fill="#7FE4D5")
    draw.line((291, 244, 750, 244), fill="#2F6DF6", width=5)
    draw.text((58, 316), "PLANS  •  MEASUREMENTS  •  INSTALLATION", font=font(FONT_SEMIBOLD, 29), fill="#B9C8DC")
    draw.text((58, 374), "Practical calculations. Offline by design.", font=font(FONT_REGULAR, 28), fill="#F7F9FD")

    target = OUT / "feature-graphic-1024x500.png"
    image.save(target, "PNG", optimize=True)
    return target


if __name__ == "__main__":
    home = ROOT / "artifacts" / "PlanRuler-1.5.0-release-emulator.png"
    home_target = OUT / "01-home.png"
    if home.exists() and not home_target.exists():
        home_target.write_bytes(home.read_bytes())
    for artifact in (create_icon(), create_feature_graphic(), home_target):
        print(artifact.name)
