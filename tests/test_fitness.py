import json
import os
from types import SimpleNamespace

import genetic_algorithm.fitness as fitness
from genetic_algorithm.genome import Genome


def test_fitness_evaluate_sets_progress_and_cleans_temp_files(monkeypatch, tmp_path) -> None:
    # Prepare a fake simulation output file.
    out_path = tmp_path / "out.json"
    out_path.write_text(json.dumps({"samples": [{"progress": 0.25}, {"progress": 0.75}]}))

    # Capture the temp bridge file path created by evaluate().
    created = {"temp_path": None}

    class _FakeTempFile:
        def __init__(self, name: str):
            self.name = name

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def write(self, s: str) -> int:
            return (tmp_path / os.path.basename(self.name)).write_text(s)

    def fake_named_tempfile(*args, **kwargs):
        p = tmp_path / "bridge_tmp.json"
        created["temp_path"] = str(p)

        # Ensure the file exists; json.dump will write via file handle methods.
        p.write_text("")
        return _FakeTempFile(str(p))

    monkeypatch.setattr(fitness.tempfile, "NamedTemporaryFile", fake_named_tempfile)

    def fake_run(*args, **kwargs):
        return SimpleNamespace(stdout=str(out_path))

    monkeypatch.setattr(fitness.subprocess, "run", fake_run)

    g = Genome(
        bridge_manual={
            "joints": [{"x": 0, "y": 0, "fixed": True}],
            "edges": [],
            "cost": 0,
        }
    )
    result = fitness.evaluate(g)

    assert result == g.fitness
    assert g.progress == 0.75
    # evaluate() should delete both temp bridge file and simulation output file.
    assert created["temp_path"] is not None
    assert not os.path.exists(created["temp_path"])
    assert not out_path.exists()
