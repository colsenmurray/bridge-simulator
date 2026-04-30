from genetic_algorithm.genome import Genome
import tempfile
import json
import subprocess
import os

MAX_COST = 30_000
DEFAULT_MAX_STEPS = 500

# Fitness weights (tune as needed)
W_PROGRESS = 1000.0
W_COST = 200.0
W_TIME = 50.0

# Event penalties (scaled by (1 - progress))
PENALTY_CRASH = 800.0
PENALTY_FALL = 1200.0

def evaluate_fitness(genome: Genome, level_name: str = "01"):
    temp_path = None
    output_path = None

    try:
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
            json.dump(genome.bridge, f)
            temp_path = f.name

        BASE_DIR = os.path.dirname(os.path.abspath(__file__))
        EXEC_PATH = os.path.join(BASE_DIR, '..', 'execute_headless.sh')
        # BRIDGE_PATH = os.path.join(BASE_DIR, '..', 'res', 'bridges', '01.json')

        result = subprocess.run(
            [EXEC_PATH, '--level', level_name, '--bridge', temp_path, '--max-steps', '500'],
            capture_output=True,
            text=True
        )

        # Simulator is expected to print the output json path, but JVM warnings/logs
        # can appear alongside it. Be tolerant and extract the last *.json line.
        combined = "\n".join(
            s for s in [result.stdout, result.stderr] if isinstance(s, str) and s.strip()
        )
        candidates = []
        for line in combined.splitlines():
            line = line.strip()
            if line.endswith(".json"):
                candidates.append(line)

        output_path = candidates[-1] if candidates else ""

        if not output_path.endswith(".json"):
            raise RuntimeError(
                "Unexpected simulation output (expected a .json path). "
                f"stdout={result.stdout!r} stderr={result.stderr!r}"
            )

        with open(output_path, "r") as f:
            output = json.load(f)

        samples = output.get("samples", [])
        if not samples:
            progress = 0.0
            timesteps = 0
        else:
            progress = float(samples[-1].get("progress", 0.0))
            timesteps = int(output.get("timestepsRun", len(samples)))

        # Event flags (backward compatible)
        crashed = bool(output.get("crashed", False) or output.get("endReason") == "crash")
        fell = bool(output.get("fell", False) or output.get("endReason") == "fell")

        max_steps = int(output.get("maxTimesteps", DEFAULT_MAX_STEPS))
        if max_steps <= 0:
            max_steps = DEFAULT_MAX_STEPS

        # Normalize components to ~[0,1]
        p = max(0.0, min(1.0, progress))
        c = min(1.0, float(getattr(genome, "cost", 0.0)) / float(MAX_COST))
        t = min(1.0, float(timesteps) / float(max_steps))

        fitness = (W_PROGRESS * p) - (W_COST * c) - (W_TIME * t)

        if fell:
            fitness -= PENALTY_FALL * (1.0 - p)
        elif crashed:
            fitness -= PENALTY_CRASH * (1.0 - p)

        genome.progress = progress
        genome.fitness = fitness

        return fitness
    
    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)

        if output_path and os.path.exists(output_path):
            os.remove(output_path)

def evaluate(genome: Genome, level_name: str = "01"):
    return evaluate_fitness(genome, level_name=level_name)

if __name__ == "__main__":
    genome = Genome(bridge_json_path="res/bridges/01_broken.json")
    fitness = evaluate_fitness(genome, "01")
    print(fitness)