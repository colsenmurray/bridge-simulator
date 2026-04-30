import random

from genetic_algorithm.genome import Genome
from genetic_algorithm.mutation import mutate


def test_mutation_preserves_uuid_strings() -> None:
    g = Genome(
        bridge_manual={
            "joints": [
                {"x": 0, "y": 0, "fixed": True, "uuid": "A"},
                {"x": 1, "y": 0, "fixed": False, "uuid": "B"},
            ],
            "edges": [{"from": 0, "to": 1, "material": "ASPHALT", "uuid": "AB"}],
        }
    )

    random.seed(0)
    child = mutate(g)
    assert all(isinstance(j.get("uuid"), str) and j["uuid"] for j in child.joints)
    assert all(isinstance(e.get("uuid"), str) and e["uuid"] for e in child.edges)
