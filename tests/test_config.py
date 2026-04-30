from genetic_algorithm.config import GAConfig


def test_config_roundtrip_yaml(tmp_path) -> None:
    cfg = GAConfig(
        population_size=10,
        generations=2,
        crossover_rate=0.7,
        mutation_rate=0.2,
        tournament_size=3,
        elitism=1,
        seed=123,
        level="01",
        initial_individual="res/bridges/01.json",
    )

    p = tmp_path / "cfg.yml"
    cfg.save_to_yaml(str(p))
    loaded = GAConfig.load_from_yaml(str(p))

    assert loaded.population_size == 10
    assert loaded.generations == 2
    assert loaded.seed == 123
    assert loaded.level == "01"
    assert loaded.initial_individual.endswith("01.json")
