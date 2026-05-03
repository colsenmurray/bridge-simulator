"""
Render an MP4 stepping through best_individual_<gen>.json snapshots in an output folder.

Static matplotlib render (terrain + bridge) matching simulator world coords and palette.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import warnings
from pathlib import Path
from typing import Any, Optional

import imageio.v3 as iio
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np
import yaml

# Repository root (bridge-simulator/)
PROJECT_ROOT = Path(__file__).resolve().parent.parent

WORLD_WIDTH_M = 100.0

# Palette from Java (beams, nodes, RiverBank)
COLOR_RIVERBANK = "#49a03f"
COLOR_SKY = "#cfe6ff"
COLOR_OUTLINE = "#000000"
COLOR_WOOD = "#ba754a"
COLOR_STEEL = "#8d92b2"
COLOR_ASPHALT = "#333333"
COLOR_FIXED_JOINT = "#d33d3d"
COLOR_MOBILE_JOINT = "#e3f069"

MATERIAL_COLORS: dict[str, str] = {
    "WOOD": COLOR_WOOD,
    "STEEL": COLOR_STEEL,
    "ASPHALT": COLOR_ASPHALT,
}

_SKY_RGB = tuple(int(COLOR_SKY[i : i + 2], 16) for i in (1, 3, 5))


def pad_frame_to_macro_block(rgb: np.ndarray, block: int = 16) -> np.ndarray:
    """Pad H,W to multiples of block (H.264) with sky color; avoids ffmpeg resize warnings."""
    h, w, c = rgb.shape
    nh = ((h + block - 1) // block) * block
    nw = ((w + block - 1) // block) * block
    if nh == h and nw == w:
        return rgb
    out = np.empty((nh, nw, c), dtype=rgb.dtype)
    out[..., 0] = _SKY_RGB[0]
    out[..., 1] = _SKY_RGB[1]
    out[..., 2] = _SKY_RGB[2]
    out[:h, :w] = rgb
    return out

BEST_INDIVIDUAL_RE = re.compile(r"^best_individual_(\d+)\.json$")


def world_height_m(pixel_width: int, pixel_height: int) -> float:
    """Match Box2D: worldHeight = pixelHeight * (worldWidth / pixelWidth)."""
    return float(pixel_height) / float(pixel_width) * WORLD_WIDTH_M


def discover_best_individuals(output_folder: Path) -> list[tuple[int, Path]]:
    """Return (generation, path) sorted by generation."""
    pairs: list[tuple[int, Path]] = []
    for p in output_folder.iterdir():
        if not p.is_file():
            continue
        m = BEST_INDIVIDUAL_RE.match(p.name)
        if m:
            pairs.append((int(m.group(1)), p))
    pairs.sort(key=lambda x: x[0])
    return pairs


def load_level_from_hyperparameters(output_folder: Path) -> str:
    hp = output_folder / "hyperparameters.yml"
    if not hp.is_file():
        raise FileNotFoundError(f"Missing hyperparameters.yml in {output_folder}")
    with open(hp, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    level = (data.get("genetic_algorithm") or {}).get("level")
    if not level:
        raise ValueError(f"No genetic_algorithm.level in {hp}")
    return str(level).strip()


def load_fitness_by_generation(output_folder: Path) -> dict[int, float]:
    """generation -> global_best_fitness from fitness_history.csv."""
    path = output_folder / "fitness_history.csv"
    if not path.is_file():
        return {}
    out: dict[int, float] = {}
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                g = int(row["generation"])
                out[g] = float(row["global_best_fitness"])
            except (KeyError, ValueError, TypeError):
                continue
    return out


def load_terrain_points(level: str) -> Optional[list[tuple[float, float]]]:
    terrain_path = PROJECT_ROOT / "res" / "terrain" / f"{level}.json"
    if not terrain_path.is_file():
        warnings.warn(f"No terrain file at {terrain_path}; drawing sky + bridge only", stacklevel=2)
        return None
    with open(terrain_path, encoding="utf-8") as f:
        data = json.load(f)
    pts = data.get("points")
    if not pts:
        return None
    return [(float(x), float(y)) for x, y in pts]


def load_bridge(path: Path) -> dict[str, Any]:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def _joint_xy(joints: list[dict[str, Any]], idx: int) -> Optional[tuple[float, float]]:
    if not (0 <= idx < len(joints)):
        return None
    j = joints[idx]
    return float(j["x"]), float(j["y"])


def render_frame(
    *,
    joints: list[dict[str, Any]],
    edges: list[dict[str, Any]],
    terrain: Optional[list[tuple[float, float]]],
    generation: int,
    fitness: Optional[float],
    cost: float,
    pixel_width: int,
    pixel_height: int,
    dpi: int = 100,
) -> np.ndarray:
    """Return RGB uint8 array (H, W, 3)."""
    wh = world_height_m(pixel_width, pixel_height)
    fig_w = pixel_width / dpi
    fig_h = pixel_height / dpi

    fig, ax = plt.subplots(
        figsize=(fig_w, fig_h),
        dpi=dpi,
        facecolor=COLOR_SKY,
    )
    ax.set_facecolor(COLOR_SKY)
    ax.set_xlim(0.0, WORLD_WIDTH_M)
    ax.set_ylim(0.0, wh)
    ax.set_aspect("equal")
    ax.axis("off")

    # Terrain (riverbank) under bridge
    if terrain and len(terrain) >= 3:
        xs = [p[0] for p in terrain]
        ys = [p[1] for p in terrain]
        ax.fill(xs, ys, color=COLOR_RIVERBANK, zorder=1, edgecolor=COLOR_OUTLINE, linewidth=1.0)

    # Beams (outline drawn first for a simple "stroke" effect)
    beam_lw = 5.0
    outline_lw = 7.0
    for e in edges:
        try:
            a = int(e["from"])
            b = int(e["to"])
        except (KeyError, ValueError, TypeError):
            continue
        pa = _joint_xy(joints, a)
        pb = _joint_xy(joints, b)
        if pa is None or pb is None:
            continue
        mat = str(e.get("material", "ASPHALT")).upper()
        fill = MATERIAL_COLORS.get(mat, COLOR_ASPHALT)
        ax.plot(
            [pa[0], pb[0]],
            [pa[1], pb[1]],
            color=COLOR_OUTLINE,
            linewidth=outline_lw,
            solid_capstyle="round",
            zorder=2,
        )
        ax.plot(
            [pa[0], pb[0]],
            [pa[1], pb[1]],
            color=fill,
            linewidth=beam_lw,
            solid_capstyle="round",
            zorder=3,
        )

    # Joints on top
    r_world = 4.0 * (WORLD_WIDTH_M / float(pixel_width))
    for j in joints:
        x, y = float(j["x"]), float(j["y"])
        fixed = bool(j.get("fixed", False))
        color = COLOR_FIXED_JOINT if fixed else COLOR_MOBILE_JOINT
        circle = plt.Circle(
            (x, y),
            radius=r_world,
            facecolor=color,
            edgecolor=COLOR_OUTLINE,
            linewidth=1.0,
            zorder=5,
        )
        ax.add_patch(circle)

    # Overlay
    n_j = len(joints)
    n_e = len(edges)
    fit_s = f"{fitness: .3f}" if fitness is not None else "n/a"
    overlay = (
        f"Gen {generation} | Fitness {fit_s} | Cost {int(round(cost))} | "
        f"Joints {n_j} | Edges {n_e}"
    )
    ax.text(
        0.02,
        0.98,
        overlay,
        transform=ax.transAxes,
        fontsize=11,
        family="monospace",
        verticalalignment="top",
        horizontalalignment="left",
        bbox=dict(boxstyle="round,pad=0.35", facecolor="white", edgecolor="black", alpha=0.85),
        zorder=10,
    )

    fig.canvas.draw()
    rgba = np.asarray(fig.canvas.buffer_rgba())
    rgb = rgba[:, :, :3].copy()
    plt.close(fig)
    return rgb


def render_learning_video(
    output_folder: str | Path,
    output_video: str | Path,
    *,
    fps: float = 2.0,
    level: Optional[str] = None,
    pixel_width: int = 960,
    pixel_height: int = 540,
) -> Path:
    out_dir = Path(output_folder).expanduser().resolve()
    vid_path = Path(output_video).expanduser()
    if not vid_path.is_absolute():
        vid_path = (Path.cwd() / vid_path).resolve()

    if level is None:
        level = load_level_from_hyperparameters(out_dir)
    fitness_map = load_fitness_by_generation(out_dir)
    terrain = load_terrain_points(level)
    snapshots = discover_best_individuals(out_dir)
    if not snapshots:
        raise FileNotFoundError(f"No best_individual_*.json files in {out_dir}")

    frames: list[np.ndarray] = []
    for gen, json_path in snapshots:
        bridge = load_bridge(json_path)
        joints = list(bridge.get("joints") or [])
        edges = list(bridge.get("edges") or [])
        cost = float(bridge.get("cost", 0.0))
        fit = fitness_map.get(gen)
        frame = render_frame(
            joints=joints,
            edges=edges,
            terrain=terrain,
            generation=gen,
            fitness=fit,
            cost=cost,
            pixel_width=pixel_width,
            pixel_height=pixel_height,
        )
        frames.append(pad_frame_to_macro_block(frame))

    vid_path.parent.mkdir(parents=True, exist_ok=True)
    iio.imwrite(
        str(vid_path),
        np.stack(frames, axis=0),
        fps=fps,
        codec="libx264",
    )
    return vid_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Render best_individual_*.json snapshots to an MP4 learning video.",
    )
    parser.add_argument(
        "--output_folder",
        type=str,
        required=True,
        help="Evolution output folder containing best_individual_*.json",
    )
    parser.add_argument(
        "--output_video",
        type=str,
        required=True,
        help="Path to write MP4 (e.g. output_9_4_final/learning.mp4)",
    )
    parser.add_argument("--fps", type=float, default=2.0, help="Frames per second (default 2)")
    parser.add_argument(
        "--level",
        type=str,
        default=None,
        help="Level id for terrain (default: read from hyperparameters.yml)",
    )
    parser.add_argument("--width", type=int, default=960, help="Frame width in pixels")
    parser.add_argument("--height", type=int, default=540, help="Frame height in pixels")
    args = parser.parse_args()

    path = render_learning_video(
        args.output_folder,
        args.output_video,
        fps=args.fps,
        level=args.level,
        pixel_width=args.width,
        pixel_height=args.height,
    )
    print(f"Wrote {path}")


if __name__ == "__main__":
    main()
