import os
import random

import pytest

from genetic_algorithm.genome import Genome
from genetic_algorithm.mutation import _add_edge
from genetic_algorithm.terrain import (
    clear_terrain_cache,
    joint_in_terrain,
    point_inside_terrain_polygon,
    set_terrain_polygon_for_tests,
)


def test_point_inside_square() -> None:
    sq = [(0.0, 0.0), (10.0, 0.0), (10.0, 10.0), (0.0, 10.0)]
    assert point_inside_terrain_polygon(5.0, 5.0, sq)
    assert not point_inside_terrain_polygon(11.0, 5.0, sq)


def test_joint_in_terrain_uses_cache() -> None:
    clear_terrain_cache()
    try:
        set_terrain_polygon_for_tests("z9", [(0.0, 0.0), (2.0, 0.0), (2.0, 2.0), (0.0, 2.0)])
        assert joint_in_terrain("z9", 1.0, 1.0)
        assert not joint_in_terrain("z9", 5.0, 5.0)
        assert not joint_in_terrain(None, 1.0, 1.0)
    finally:
        clear_terrain_cache()


@pytest.mark.skipif(
    not os.path.isfile(
        os.path.join(os.path.dirname(__file__), "..", "res", "terrain", "09.json")
    ),
    reason="res/terrain/09.json not present",
)
def test_joint_in_terrain_level09_pit_and_span() -> None:
    clear_terrain_cache()
    try:
        assert joint_in_terrain("09", 42.5, 9.0)
        assert joint_in_terrain("09", 50.0, 10.0)
        assert not joint_in_terrain("09", 16.0, 31.0)
    finally:
        clear_terrain_cache()


def test_add_edge_avoids_terrain_polygon() -> None:
    clear_terrain_cache()
    try:
        set_terrain_polygon_for_tests(
            "L",
            [(50.0, 50.0), (70.0, 50.0), (70.0, 70.0), (50.0, 70.0)],
        )
        g = Genome(
            bridge_manual={
                "joints": [
                    {"x": 48.0, "y": 48.0, "fixed": True, "uuid": "a"},
                    {"x": 50.0, "y": 48.0, "fixed": False, "uuid": "b"},
                ],
                "edges": [],
            }
        )
        for seed in range(3000):
            random.seed(seed)
            gg = g.clone()
            _add_edge(gg, 0, level="L")
            for j in gg.joints:
                assert not joint_in_terrain("L", float(j["x"]), float(j["y"]))
    finally:
        clear_terrain_cache()
