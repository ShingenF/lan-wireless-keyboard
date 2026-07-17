from __future__ import annotations

import argparse
import struct
import subprocess
import uuid
from pathlib import Path
from urllib.parse import quote

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "VirtualKeyboardReceiver" / "Assets"
SCRATCH = ROOT / ".scratch" / "icon-generation"
APPLICATION_SOURCE = ASSETS / "hugeicons-keyboard-stroke-rounded.svg"
TRAY_SOURCE = ASSETS / "hugeicons-keyboard-stroke-rounded.svg"
SIZES = (16, 20, 24, 32, 40, 48, 64, 256)


def colored_svg(source_path: Path, paint_attribute: str, color: str, size: int) -> bytes:
    source = source_path.read_text(encoding="utf-8")
    source = source.replace('height="24"', f'height="{size}"', 1)
    source = source.replace('width="24"', f'width="{size}"', 1)
    return source.replace("<svg ", f'<svg {paint_attribute}="{color}" ', 1).encode("utf-8")


def edge_executable() -> Path:
    candidates = (
        Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
        Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
    )
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise SystemExit("Microsoft Edge is required to render the official SVG source")


def file_url(path: Path) -> str:
    return "file:///" + quote(path.resolve().as_posix(), safe="/:#")


def render_frames(
    edge: Path,
    name: str,
    source_path: Path,
    paint_attribute: str,
    color: str,
    max_bound_ratio: float,
) -> list[bytes]:
    variant_dir = SCRATCH / name
    variant_dir.mkdir(parents=True, exist_ok=True)
    frames: list[bytes] = []
    for size in SIZES:
        svg_path = variant_dir / f"{name}-{size}.svg"
        svg_path.write_bytes(colored_svg(source_path, paint_attribute, color, size))
        png_path = variant_dir / f"{size}.png"
        subprocess.run(
            [
                str(edge),
                "--headless=new",
                "--disable-gpu",
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-extensions",
                "--disable-sync",
                "--hide-scrollbars",
                "--no-first-run",
                "--no-default-browser-check",
                "--force-device-scale-factor=1",
                "--default-background-color=00000000",
                f"--window-size={size},{size}",
                f"--screenshot={png_path}",
                f"--user-data-dir={SCRATCH / f'edge-profile-{name}-{size}-{uuid.uuid4().hex}'}",
                file_url(svg_path),
            ],
            check=True,
            timeout=30,
        )
        validate_frame(png_path, size, max_bound_ratio)
        frames.append(png_path.read_bytes())
    return frames


def validate_frame(path: Path, size: int, max_bound_ratio: float) -> None:
    with Image.open(path) as image:
        if image.size != (size, size) or image.mode != "RGBA":
            raise RuntimeError(f"{path.name}: expected {size}x{size} RGBA, got {image.size} {image.mode}")
        bounds = image.getchannel("A").getbbox()
        if bounds is None:
            raise RuntimeError(f"{path.name}: frame is fully transparent")
        left, top, right, bottom = bounds
        width_ratio = (right - left) / size
        height_ratio = (bottom - top) / size
        center_x = (left + right) / 2
        center_y = (top + bottom) / 2
        center_tolerance = max(1.0, size * 0.08)
        if not 0.65 <= width_ratio <= max_bound_ratio:
            raise RuntimeError(f"{path.name}: alpha width ratio {width_ratio:.3f} is unreasonable")
        if not 0.50 <= height_ratio <= max_bound_ratio:
            raise RuntimeError(f"{path.name}: alpha height ratio {height_ratio:.3f} is unreasonable")
        if abs(center_x - size / 2) > center_tolerance or abs(center_y - size / 2) > center_tolerance:
            raise RuntimeError(f"{path.name}: alpha bounds {bounds} are not centered")


def write_ico(path: Path, frames: list[bytes]) -> None:
    header_size = 6 + 16 * len(frames)
    offset = header_size
    entries: list[bytes] = []
    for size, frame in zip(SIZES, frames, strict=True):
        dimension = 0 if size == 256 else size
        entries.append(
            struct.pack(
                "<BBBBHHII",
                dimension,
                dimension,
                0,
                0,
                1,
                32,
                len(frame),
                offset,
            )
        )
        offset += len(frame)
    path.write_bytes(struct.pack("<HHH", 0, 1, len(frames)) + b"".join(entries) + b"".join(frames))
    with Image.open(path) as icon:
        actual_sizes = set(icon.ico.sizes())
    expected_sizes = {(size, size) for size in SIZES}
    if actual_sizes != expected_sizes:
        raise RuntimeError(f"{path.name}: ICO sizes {sorted(actual_sizes)} != {sorted(expected_sizes)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=("all", "application", "tray"), default="all")
    args = parser.parse_args()
    edge = edge_executable()
    SCRATCH.mkdir(parents=True, exist_ok=True)
    if args.target in ("all", "application"):
        application = render_frames(
            edge, "application-black", APPLICATION_SOURCE, "color", "#000000", 1.0
        )
        write_ico(ASSETS / "receiver.ico", application)
    if args.target in ("all", "tray"):
        tray_black = render_frames(
            edge, "tray-black", TRAY_SOURCE, "color", "#000000", 1.0
        )
        tray_white = render_frames(
            edge, "tray-white", TRAY_SOURCE, "color", "#FFFFFF", 1.0
        )
        write_ico(ASSETS / "tray-hugeicons-keyboard-black.ico", tray_black)
        write_ico(ASSETS / "tray-hugeicons-keyboard-white.ico", tray_white)


if __name__ == "__main__":
    main()
