"""
Terrain checks for mutation / repair: align with RiverBank.containsPoint and Level.terrainProfileYAt.

- Filled polygon (Java Polygon on millis-rounded coords): being inside the green terrain mesh.
- Surface clearance: y below Level.terrainProfileYAt(x) + margin (embedded in / under the road).

When the terrain chain has fewer than 5 points, only the polygon test is used (matches Java fallback).

Generate polygons with ./dump_terrain.sh --level <name> (same centering/boundary as headless
GameSession). Missing file => no constraint.
"""

from __future__ import annotations

import json
import math
import os
from typing import Any, Sequence

# Project root: genetic_algorithm/ -> bridge-simulator/
_PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# Match Level.TERRAIN_Y_AT_X_EPS
_TERRAIN_Y_AT_X_EPS = 1e-4

# Joints must be at least this far above the interpolated terrain profile (world units).
MIN_CLEARANCE_ABOVE_SURFACE = 0.55

# When lifting mutable joints out of forbidden zone, step size and max iterations.
_TERRAIN_LIFT_STEP = 0.4
_TERRAIN_LIFT_MAX_STEPS = 140

_cache: dict[str, list[tuple[float, float]] | None] = {}


def clear_terrain_cache() -> None:
    _cache.clear()


def set_terrain_polygon_for_tests(level: str, points: Sequence[tuple[float, float]] | None) -> None:
    """Pin terrain for a level (or None = treat as missing). For unit tests only."""
    _cache[str(level)] = list(points) if points is not None else None


def _read_terrain_file(level: str) -> list[tuple[float, float]] | None:
    path = os.path.join(_PROJECT_ROOT, "res", "terrain", f"{level}.json")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError):
        return None
    raw = data.get("points")
    if not isinstance(raw, list) or len(raw) < 3:
        return None
    out: list[tuple[float, float]] = []
    for item in raw:
        if not isinstance(item, (list, tuple)) or len(item) < 2:
            continue
        try:
            out.append((float(item[0]), float(item[1])))
        except (TypeError, ValueError):
            continue
    return out if len(out) >= 3 else None


def load_terrain_polygon(level: str | None) -> list[tuple[float, float]] | None:
    if not level:
        return None
    key = str(level)
    if key not in _cache:
        _cache[key] = _read_terrain_file(key)
    poly = _cache[key]
    return poly


def _java_round_milli(v: float) -> int:
    """Match Java Math.round(double) on v*1000 for polygon vertices (half-up via floor(x+0.5))."""
    return int(math.floor(float(v) * 1000.0 + 0.5))


def terrain_surface_y_at(x: float, terrain_points: Sequence[tuple[float, float]]) -> float:
    """
    Same as Level.terrainProfileYAt: use indices 2 … size-3 of getTerrainPoints().
    """
    t = terrain_points
    n = len(t)
    p0 = 2
    p_end = n - 3
    if n < 5 or p_end < p0:
        if n > 2:
            return float(t[2][1])
        return 0.0
    if x <= float(t[p0][0]):
        return float(t[p0][1])
    if x >= float(t[p_end][0]):
        return float(t[p_end][1])
    for i in range(p0, p_end):
        a = t[i]
        b = t[i + 1]
        x_lo = min(a[0], b[0])
        x_hi = max(a[0], b[0])
        if x < x_lo - _TERRAIN_Y_AT_X_EPS or x > x_hi + _TERRAIN_Y_AT_X_EPS:
            continue
        if abs(b[0] - a[0]) < 1e-5:
            return 0.5 * (float(a[1]) + float(b[1]))
        u = (x - float(a[0])) / (float(b[0]) - float(a[0]))
        u = max(0.0, min(1.0, u))
        return float(a[1]) + u * (float(b[1]) - float(a[1]))
    return 0.5 * (float(t[p0][1]) + float(t[p_end][1]))


def point_inside_terrain_polygon(x: float, y: float, terrain_points: Sequence[tuple[float, float]]) -> bool:
    """
    Same construction as RiverBank.containsPoint: java.awt.Polygon with vertices
    (Math.round(point.x * 1000), ...), then contains on query point.
    """
    xs = [_java_round_milli(p[0]) for p in terrain_points]
    ys = [_java_round_milli(p[1]) for p in terrain_points]
    px = _java_round_milli(x)
    py = _java_round_milli(y)
    n = len(xs)
    if n < 3:
        return False
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = xs[i], ys[i]
        xj, yj = xs[j], ys[j]
        if (yi > py) != (yj > py):
            denom = float(yj - yi)
            if denom == 0.0:
                intersect_x = float(xi)
            else:
                intersect_x = float(xj - xi) * float(py - yi) / denom + float(xi)
            if float(px) < intersect_x:
                inside = not inside
        j = i
    return inside


def joint_in_terrain(level: str | None, x: float, y: float) -> bool:
    """
    True if (x,y) is invalid for a free joint: inside the filled terrain polygon and/or
    too far below the road profile (when level has >= 5 terrain vertices).
    """
    poly = load_terrain_polygon(level)
    if not poly:
        return False
    inside = point_inside_terrain_polygon(x, y, poly)
    if len(poly) < 5:
        return inside
    prof = terrain_surface_y_at(x, poly)
    below = y < prof + MIN_CLEARANCE_ABOVE_SURFACE
    return inside or below


def repair_mutable_joints_out_of_terrain(bridge: dict[str, Any], level: str | None) -> None:
    """
    Lift non-fixed joints until joint_in_terrain is false (or cap iterations).
    Fixed anchors are left unchanged.
    """
    if not level or not load_terrain_polygon(level):
        return
    joints = bridge.get("joints")
    if not isinstance(joints, list):
        return
    for j in joints:
        if not isinstance(j, dict) or j.get("fixed", False):
            continue
        x = float(j["x"])
        y = float(j["y"])
        for _ in range(_TERRAIN_LIFT_MAX_STEPS):
            if not joint_in_terrain(level, x, y):
                break
            y += _TERRAIN_LIFT_STEP
        j["y"] = y
