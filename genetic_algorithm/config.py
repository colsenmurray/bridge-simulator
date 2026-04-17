from typing import Optional
import yaml

class GAConfig:
    def __init__(self, population_size: int, generations: int, crossover_rate: float, mutation_rate: float, tournament_size: int, elitism: int, seed: Optional[int]):
        self.population_size = population_size
        self.generations = generations
        self.crossover_rate = crossover_rate
        self.mutation_rate = mutation_rate
        self.tournament_size = tournament_size
        self.elitism = elitism
        self.seed = seed

    def save_to_yaml(self, yaml_path: str):
        with open(yaml_path, 'w') as f:
            yaml.dump(self.__dict__, f, default_flow_style=False)

    @staticmethod
    def load_from_yaml(yaml_path: str):
        with open(yaml_path, 'r') as f:
            return yaml.load(f, Loader=yaml.FullLoader)