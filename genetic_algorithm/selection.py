from genetic_algorithm.genome import Genome
import random

# pick random contestants and return the one with the highest fitness score
def select_tournament(population: list[Genome], tournament_size: int = 3):
    contestants = random.sample(population, tournament_size)
    highest = max(contestants, key=lambda g: g.fitness)
    return highest

def select_elitism(population: list[Genome], elitism_rate: float = 0.1):
    elitism_size = int(len(population) * elitism_rate)
    return population[:elitism_size]