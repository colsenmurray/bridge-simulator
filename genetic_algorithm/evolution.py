from genetic_algorithm.config import GAConfig
from genetic_algorithm.genome import Genome
from genetic_algorithm.crossover import crossover
from genetic_algorithm.fitness import evaluate_fitness

class Evolution:
    def __init__(self, config: GAConfig):
        self.config = config
        self.population = self.initialize_population()
        self.best_fitness = float('-inf')
        self.best_individual = None

    def initialize_population(self):
        initial_individual = Genome(bridge_json_path=self.config.initial_individual)
        return [initial_individual.clone() for _ in range(self.config.population_size)]

    def evaluate_population(self):
        for individual in self.population:
            fitness = evaluate_fitness(individual)
            individual.fitness = fitness
            if fitness > self.best_fitness:
                self.best_fitness = fitness
                self.best_individual = individual

    def evolve_population(self):
        pass

    


    def run(self):
        
        for generation in range(self.config.generations):
            pass