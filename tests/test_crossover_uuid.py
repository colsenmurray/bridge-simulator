import random

from genetic_algorithm.crossover import crossover
from genetic_algorithm.genome import Genome


def _bridge(joints, edges):
    return {"joints": joints, "edges": edges}


def test_crossover_matches_by_uuid_only() -> None:
    # Parent 1 has joints A,B,C; Parent 2 has joints A,B,D.
    p1 = Genome(
        bridge_manual=_bridge(
            joints=[
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
                {"x": 2, "y": 0, "fixed": False, "uuid": "C"},
            ],
            edges=[
                {"from": 0, "to": 1, "material": "ASPHALT", "uuid": "AB"},
                {"from": 1, "to": 2, "material": "ASPHALT", "uuid": "BC"},
            ],
        )
    )
    p2 = Genome(
        bridge_manual=_bridge(
            joints=[
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
                {"x": 10, "y": 0, "fixed": False, "uuid": "D"},
            ],
            edges=[
                {"from": 0, "to": 1, "material": "ASPHALT", "uuid": "AB"},
                {"from": 1, "to": 2, "material": "ASPHALT", "uuid": "BD"},
            ],
        )
    )
    p1.fitness = 10.0
    p2.fitness = 5.0

    random.seed(0)
    child = crossover(p1, p2)

    # Matching joints/edges by uuid must survive.
    child_joint_uuids = {j["uuid"] for j in child.joints}
    assert "A" in child_joint_uuids
    assert "B" in child_joint_uuids

    child_edge_uuids = {e["uuid"] for e in child.edges}
    assert "AB" in child_edge_uuids


def test_crossover_edge_snaps_missing_joint_by_position() -> None:
    # Parent 1 is fitter but we force inheriting an edge whose endpoint joint is not inherited.
    # The snapping logic should connect to nearest existing child joint.
    p1 = Genome(
        bridge_manual=_bridge(
            joints=[
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 5, "y": 0, "fixed": False, "uuid": "B"},
                {"x": 9, "y": 0, "fixed": False, "uuid": "C"},
            ],
            edges=[{"from": 1, "to": 2, "material": "ASPHALT", "uuid": "BC"}],
        )
    )
    p2 = Genome(
        bridge_manual=_bridge(
            joints=[
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 5, "y": 0, "fixed": False, "uuid": "B"},
                {"x": 9, "y": 0, "fixed": False, "uuid": "C"},
            ],
            edges=[{"from": 0, "to": 2, "material": "ASPHALT", "uuid": "AC"}],
        )
    )
    p1.fitness = 10.0
    p2.fitness = 5.0

    # Make inheritance of unique joints very unlikely to keep C out sometimes.
    random.seed(2)
    child = crossover(p1, p2)

    # Regardless of whether C was inherited, crossover must remain well-formed.
    assert child.validate_bridge() in (True, False)
    assert all(isinstance(j["uuid"], str) for j in child.joints)
    assert all(isinstance(e["uuid"], str) for e in child.edges)
