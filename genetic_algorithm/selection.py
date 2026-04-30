from genome import Genome
import random

# pick random contestants and return the one with the highest fitness score
def select_tournament(population: list[Genome], tournament_size: int = 3):
    contestants = random.sample(population, tournament_size)
    
    highest = max(contestants, key=lambda g: g.fitness)
    
    return highest.clone()
