from genetic_algorithm.genome import Genome


def test_edges_get_from_to_uuid_fields() -> None:
    g = Genome(
        bridge_manual={
            "joints": [
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
            ],
            "edges": [{"from": 0, "to": 1, "material": "ASPHALT", "uuid": "AB"}],
        }
    )

    assert len(g.edges) == 1
    e = g.edges[0]
    assert e["from_uuid"] == "A"
    assert e["to_uuid"] == "B"


def test_reindex_edges_from_uuids_updates_indices() -> None:
    bridge = {
        "joints": [
            {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
            {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
            {"x": 2, "y": 0, "fixed": False, "uuid": "C"},
        ],
        "edges": [
            {"from": 0, "to": 2, "material": "ASPHALT", "uuid": "AC", "from_uuid": "A", "to_uuid": "C"},
        ],
    }
    g = Genome(bridge_manual=bridge)

    # Remove the middle joint by list manipulation, then reindex edges by uuids.
    g.joints.pop(1)  # removes B, so C shifts from index 2 -> 1
    g.bridge["joints"] = g.joints
    Genome._reindex_edges_from_uuids_inplace(g.bridge)

    assert g.bridge["edges"][0]["from"] == 0
    assert g.bridge["edges"][0]["to"] == 1


def test_remove_joint_drops_incident_edges_and_reindexes_remaining() -> None:
    # Remove joint B; edge AB and BC should be dropped; AC should be reindexed correctly.
    g = Genome(
        bridge_manual={
            "joints": [
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
                {"x": 2, "y": 0, "fixed": True, "uuid": "C"},
            ],
            "edges": [
                {"from": 0, "to": 1, "material": "ASPHALT", "uuid": "AB"},
                {"from": 1, "to": 2, "material": "ASPHALT", "uuid": "BC"},
                {"from": 0, "to": 2, "material": "ASPHALT", "uuid": "AC"},
            ],
        }
    )

    # Manually emulate the logic: remove B and incident edges, then reindex.
    removed_uuid = g.joints[1]["uuid"]
    g.joints.pop(1)
    g.bridge["joints"] = g.joints
    g.bridge["edges"] = [e for e in g.edges if e["from_uuid"] != removed_uuid and e["to_uuid"] != removed_uuid]
    Genome._reindex_edges_from_uuids_inplace(g.bridge)

    assert len(g.bridge["edges"]) == 1
    e = g.bridge["edges"][0]
    assert e["from_uuid"] == "A"
    assert e["to_uuid"] == "C"
    assert e["from"] == 0
    assert e["to"] == 1

