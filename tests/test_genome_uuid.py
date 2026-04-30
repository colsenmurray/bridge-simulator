import json

from genetic_algorithm.genome import Genome


def test_genome_manual_bridge_fills_missing_uuids() -> None:
    bridge = {
        "joints": [{"x": 0, "y": 0, "fixed": True}, {"x": 1, "y": 0, "fixed": False}],
        "edges": [{"from": 0, "to": 1, "material": "ASPHALT"}],
    }
    g = Genome(bridge_manual=bridge)

    assert all(isinstance(j.get("uuid"), str) and j["uuid"] for j in g.joints)
    assert all(isinstance(e.get("uuid"), str) and e["uuid"] for e in g.edges)


def test_genome_loaded_bridge_preserves_existing_uuid_strings(tmp_path) -> None:
    data = {
        "joints": [
            {"x": 0, "y": 0, "fixed": True, "uuid": "joint-a"},
            {"x": 1, "y": 0, "fixed": False, "uuid": "joint-b"},
        ],
        "edges": [{"from": 0, "to": 1, "material": "ASPHALT", "uuid": "edge-ab"}],
    }
    p = tmp_path / "bridge.json"
    p.write_text(json.dumps(data))

    g = Genome(bridge_json_path=str(p))
    assert g.joints[0]["uuid"] == "joint-a"
    assert g.joints[1]["uuid"] == "joint-b"
    assert g.edges[0]["uuid"] == "edge-ab"


def test_genome_loaded_bridge_coerces_numeric_uuid_to_string(tmp_path) -> None:
    data = {
        "joints": [{"x": 0, "y": 0, "fixed": True, "uuid": 123}],
        "edges": [],
    }
    p = tmp_path / "bridge.json"
    p.write_text(json.dumps(data))

    g = Genome(bridge_json_path=str(p))
    assert g.joints[0]["uuid"] == "123"


def test_validate_bridge_requires_path_between_extreme_fixed_joints() -> None:
    # Fixed joints at x=0 and x=10 are not connected -> invalid
    g1 = Genome(
        bridge_manual={
            "joints": [
                {"x": 0, "y": 0, "fixed": True, "uuid": "L"},
                {"x": 10, "y": 0, "fixed": True, "uuid": "R"},
                {"x": 5, "y": 0, "fixed": False, "uuid": "M"},
            ],
            "edges": [],
        }
    )
    assert g1.validate_bridge() is False

    # Add a connecting path L-M-R -> valid (subject to other constraints)
    g2 = Genome(
        bridge_manual={
            "joints": [
                {"x": 0, "y": 0, "fixed": True, "uuid": "L"},
                {"x": 10, "y": 0, "fixed": True, "uuid": "R"},
                {"x": 5, "y": 0, "fixed": False, "uuid": "M"},
            ],
            "edges": [
                {"from": 0, "to": 2, "material": "ASPHALT", "uuid": "LM"},
                {"from": 2, "to": 1, "material": "ASPHALT", "uuid": "MR"},
            ],
        }
    )
    assert g2.validate_bridge() is True
