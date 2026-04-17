from typing import Optional

class GAConfig:
    def __init__(self, population_size: int, generations: int, crossover_rate: float, mutation_rate: float, tournament_size: int, elitism: int, seed: Optional[int]):
        self.population_size = population_size
        self.generations = generations
        self.crossover_rate = crossover_rate
        self.mutation_rate = mutation_rate
        self.tournament_size = tournament_size
        self.elitism = elitism
        self.seed = seed