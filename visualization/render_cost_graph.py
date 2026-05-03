"""
Plot bridge cost vs generation from best_individual_<gen>.json snapshots in an output folder.

CLI mirrors render_video.py: pass --output_folder; the figure is written there by default.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

BEST_INDIVIDUAL_RE = re.compile(r"^best_individual_(\d+)\.json$")


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


def load_bridge(path: Path) -> dict[str, Any]:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def render_cost_graph(
    output_folder: str | Path,
    output_figure: str | Path | None = None,
    *,
    fig_width: float = 10.0,
    fig_height: float = 5.0,
    dpi: int = 120,
) -> Path:
    out_dir = Path(output_folder).expanduser().resolve()
    if output_figure is None:
        fig_path = out_dir / "cost_graph.png"
    else:
        fig_path = Path(output_figure).expanduser()
        if not fig_path.is_absolute():
            fig_path = (Path.cwd() / fig_path).resolve()

    snapshots = discover_best_individuals(out_dir)
    if not snapshots:
        raise FileNotFoundError(f"No best_individual_*.json files in {out_dir}")

    generations: list[int] = []
    costs: list[float] = []
    for gen, json_path in snapshots:
        bridge = load_bridge(json_path)
        generations.append(gen)
        costs.append(float(bridge.get("cost", 0.0)))

    fig, ax = plt.subplots(figsize=(fig_width, fig_height), dpi=dpi)
    ax.plot(generations, costs, color="#2c6cb0", linewidth=1.8, marker="o", markersize=4)
    ax.set_xlabel("Generation")
    ax.set_ylabel("Bridge cost")
    ax.set_title("Cost of saved best individuals")
    ax.grid(True, alpha=0.35)
    fig.tight_layout()

    fig_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(fig_path, dpi=dpi, bbox_inches="tight")
    plt.close(fig)
    return fig_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Plot cost vs generation from best_individual_*.json in an output folder.",
    )
    parser.add_argument(
        "--output_folder",
        type=str,
        required=True,
        help="Evolution output folder containing best_individual_*.json",
    )
    parser.add_argument(
        "--output_figure",
        type=str,
        default=None,
        help="Path to write PNG (default: <output_folder>/cost_graph.png)",
    )
    parser.add_argument("--width", type=float, default=10.0, help="Figure width in inches (default 10)")
    parser.add_argument("--height", type=float, default=5.0, help="Figure height in inches (default 5)")
    parser.add_argument("--dpi", type=int, default=120, help="Rasterization DPI (default 120)")
    args = parser.parse_args()

    path = render_cost_graph(
        args.output_folder,
        args.output_figure,
        fig_width=args.width,
        fig_height=args.height,
        dpi=args.dpi,
    )
    print(f"Wrote {path}")


if __name__ == "__main__":
    main()
