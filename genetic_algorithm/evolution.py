from genetic_algorithm.config import GAConfig
from genetic_algorithm.genome import Genome
from genetic_algorithm.crossover import crossover
from genetic_algorithm.fitness import evaluate_fitness, get_fitness_hyperparameters
from genetic_algorithm.selection import select_tournament
from genetic_algorithm.mutation import mutate
import csv
import random
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Optional

import yaml

RESET = "\033[0m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
BOLD = "\033[1m"

class Evolution:
    def __init__(
        self,
        config: GAConfig,
        output_folder: str,
        *,
        config_source_path: Optional[str] = None,
    ):
        self.config = config
        self._config_source_path = config_source_path
        if self.config.seed is not None:

            random.seed(int(self.config.seed))
        self.best_fitness = float('-inf')
        self.best_individual = None
        self.best_fitness_in_generation = float('-inf')
        self.best_individual_in_generation = None
        self.output_folder = output_folder
        self.generation = 0
        self.best_fitness_history = []
        self.best_individual_history = []
        self._fitness_csv_path = os.path.join(self.output_folder, "fitness_history.csv")
        self._hyperparameters_path = os.path.join(self.output_folder, "hyperparameters.yml")
        self.fail_streak = 0
        self.population = self.initialize_population()

    def _write_hyperparameters(self) -> None:
        payload = {
            "run_metadata": {
                "started_at_utc": datetime.now(timezone.utc).isoformat(),
                **(
                    {"config_file": os.path.abspath(self._config_source_path)}
                    if self._config_source_path
                    else {}
                ),
            },
            "genetic_algorithm": dict(vars(self.config)),
            "fitness": get_fitness_hyperparameters(),
        }
        with open(self._hyperparameters_path, "w", encoding="utf-8") as f:
            yaml.safe_dump(payload, f, default_flow_style=False, sort_keys=False, allow_unicode=True)

    def _append_fitness_history_row(
        self,
        generation: int,
        best_in_population: float,
        mean_fitness: float,
        global_best_fitness: float,
    ) -> None:
        write_header = not os.path.exists(self._fitness_csv_path)
        with open(self._fitness_csv_path, "a", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            if write_header:
                w.writerow(
                    [
                        "generation",
                        "best_in_population",
                        "mean_fitness",
                        "global_best_fitness",
                    ]
                )
            w.writerow(
                [
                    generation,
                    f"{best_in_population:.6f}",
                    f"{mean_fitness:.6f}",
                    f"{global_best_fitness:.6f}",
                ]
            )

    def initialize_population(self):
        print(f"[evolution] init population size={self.config.population_size} from={self.config.initial_individual}")
        initial_individual = Genome(bridge_json_path=self.config.initial_individual)
        pop = []
        for _ in range(self.config.population_size):
            child = initial_individual.clone()
            for _ in range(10):
                child = mutate(child, self.fail_streak)
            pop.append(child)
        print(f"[evolution] init done joints={len(pop[0].joints)} edges={len(pop[0].edges)}")
        return pop

    def evaluate_population(self):
        print(f"[evolution] evaluating population n={len(self.population)} level={self.config.level}")
        best_in_pop = float("-inf")
        total = 0.0

        # Minimal CPU parallelization: each fitness call spawns a headless subprocess,
        # so threads work well (GIL isn't the bottleneck).
        max_workers = min(len(self.population), (os.cpu_count() or 4))
        with ThreadPoolExecutor(max_workers=max_workers) as ex:
            future_to_individual = {
                ex.submit(evaluate_fitness, individual, self.config.level): individual
                for individual in self.population
            }
            for fut in as_completed(future_to_individual):
                individual = future_to_individual[fut]
                fitness = fut.result()
                individual.fitness = fitness
                total += float(fitness)
                best_in_pop = max(best_in_pop, float(fitness))
                if fitness > self.best_fitness:
                    self.best_fitness = fitness
                    self.best_individual = individual
                    self.save_best_individual()
        avg = total / max(1, len(self.population))
        print(f"[evolution] eval done best={best_in_pop:.3f} avg={avg:.3f} global_best={float(self.best_fitness):.3f}")
        if self.best_individual.end_reason == "stuck" or self.best_individual.end_reason == "crash" or self.best_individual.end_reason == "fell":
            self.fail_streak += 1
        else:
            self.fail_streak = 0
        return best_in_pop, avg

    def evolve_population(self):
        # Elitism: keep top-N unchanged (by fitness)
        elitism_n = max(0, min(int(self.config.elitism), len(self.population)))
        sorted_pop = sorted(self.population, key=lambda g: float(g.fitness), reverse=True)
        elites = [g.clone() for g in sorted_pop[:elitism_n]]
        print(f"[evolution] elites kept={len(elites)}")

        # Fill the rest via tournament selection + crossover.
        next_pop: list[Genome] = list(elites)
        target_n = int(self.config.population_size)
        while len(next_pop) < target_n:
            p1 = select_tournament(self.population, tournament_size=int(self.config.tournament_size))
            p2 = select_tournament(self.population, tournament_size=int(self.config.tournament_size))


            p1_clone = p1.clone()
            p2_clone = p2.clone()
            
            if len(next_pop) >= int(self.config.population_size * 0.9):
                mutations_count = random.randint(0, (self.fail_streak + 10))
                for _ in range(mutations_count):
                    if random.random() < float(self.config.mutation_rate):
                        p1_clone = mutate(p1_clone, self.fail_streak)
                        p2_clone = mutate(p2_clone, self.fail_streak)
            else:
                if random.random() < float(self.config.mutation_rate):
                    p1_clone = mutate(p1_clone, self.fail_streak)
                    p2_clone = mutate(p2_clone, self.fail_streak)
                    



            if random.random() < float(self.config.crossover_rate):
                child = crossover(p1_clone, p2_clone)
            else:
                child = p1_clone if random.random() < 0.5 else p2_clone

            next_pop.append(child)

        print(f"[evolution] evolved next population n={len(next_pop)}")
        return next_pop

    def save_best_individual(self):
        if self.best_individual is not None:
            self.best_fitness_history.append(float(self.best_fitness))
            self.best_individual_history.append(self.best_individual.clone())
            self.best_individual.save_to_json(os.path.join(self.output_folder, f"best_individual_{self.generation}.json"))
            print(f"{BOLD}{GREEN}Best individual saved - Fitness: {self.best_fitness:.3f}{RESET}")

    def run(self):
        print(f"[evolution] run start generations={self.config.generations}")
        os.makedirs(self.output_folder, exist_ok=True)
        self._write_hyperparameters()

        best_in_pop, avg = self.evaluate_population()
        self._append_fitness_history_row(
            0,
            best_in_pop,
            avg,
            float(self.best_fitness),
        )

        for generation in range(self.config.generations):
            self.generation += 1
            self.population = self.evolve_population()
            best_in_pop, avg = self.evaluate_population()
            self._append_fitness_history_row(
                self.generation,
                best_in_pop,
                avg,
                float(self.best_fitness),
            )
            # Track best individual seen so far (already maintained in evaluate_population)
            print(f"[evolution] --------------------------------")
            print(f"[evolution] End Reason: {self.best_individual.end_reason}")
            print(f"[evolution] Progress: {self.best_individual.progress}")
            print(f"[evolution] generation {generation+1}/{self.config.generations}")
            print(f"[evolution] global_best={float(self.best_fitness):.3f}")

        return self.best_individual