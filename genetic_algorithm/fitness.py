from genetic_algorithm.genome import Genome
import tempfile
import json
import subprocess
import os
import math

MAX_COST = 30_000
DEFAULT_MAX_STEPS = 1000

# Fitness weights (tune as needed)
W_PROGRESS = 1000.0
W_COST = 700.0
W_TIME = 0.0

# Event penalties (scaled by (1 - progress))
PENALTY_CRASH = 1000.0
PENALTY_FALL = 1000.0
PENALTY_STUCK = 1000.0

_BEAM_MAX_LENGTH_FOR_PRICING = 8.0  # must match bridge.physics.beams.Beam.MAX_LENGTH
_MATERIAL_UNIT_PRICE: dict[str, int] = {
    # must match unit-price multipliers in AsphaltBeam/WoodBeam/SteelBeam
    "WOOD": 1000,
    "STEEL": 2000,
    "ASPHALT": 3000,
}


def get_fitness_hyperparameters() -> dict:
    """Serializable snapshot of fitness shaping constants (for experiment logs)."""
    return {
        "MAX_COST": MAX_COST,
        "DEFAULT_MAX_STEPS": DEFAULT_MAX_STEPS,
        "W_PROGRESS": W_PROGRESS,
        "W_COST": W_COST,
        "W_TIME": W_TIME,
        "PENALTY_CRASH": PENALTY_CRASH,
        "PENALTY_FALL": PENALTY_FALL,
        "BEAM_MAX_LENGTH_FOR_PRICING": _BEAM_MAX_LENGTH_FOR_PRICING,
        "MATERIAL_UNIT_PRICE": dict(_MATERIAL_UNIT_PRICE),
    }


def _recompute_cost_inplace(genome: Genome) -> float:
    """
    Recompute bridge cost from current joints/edges.

    Mirrors Java pricing logic:
      price(edge) = round(edge_length / MAX_LENGTH * materialUnitPrice)
    where MAX_LENGTH=8 and unit prices are WOOD=1000, STEEL=2000, ASPHALT=3000.
    """
    total = 0.0
    joints = genome.joints
    n = len(joints)
    for e in genome.edges:
        try:
            a = int(e["from"])
            b = int(e["to"])
        except (KeyError, ValueError, TypeError):
            continue
        if not (0 <= a < n and 0 <= b < n) or a == b:
            continue

        material = str(e.get("material", "ASPHALT")).upper()
        unit_price = _MATERIAL_UNIT_PRICE.get(material, _MATERIAL_UNIT_PRICE["ASPHALT"])

        length = float(genome.edge_length(a, b))
        # Match Java's Math.round(float):
        #   (int) floor(x + 0.5) for non-negative x
        x = (length / _BEAM_MAX_LENGTH_FOR_PRICING) * float(unit_price)
        price = int(math.floor(x + 0.5))
        total += float(price)

    genome.cost = float(total)
    genome.bridge["cost"] = float(total)
    return float(total)


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
            [EXEC_PATH, '--level', level_name, '--bridge', temp_path, '--max-steps', str(DEFAULT_MAX_STEPS)],
            capture_output=True,
            text=True
        )

        # Simulator is expected to print the output json path, but JVM warnings/logs
        # can appear alongside it. Be tolerant and extract the last *.json line.
        stderr = getattr(result, "stderr", None) or ""
        combined = "\n".join(
            s for s in [result.stdout, stderr] if isinstance(s, str) and s.strip()
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

        genome.end_reason = output.get("endReason", None)

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
        stuck = bool(output.get("stuck", False) or output.get("endReason") == "stuck")

        max_steps = int(output.get("maxTimesteps", DEFAULT_MAX_STEPS))
        if max_steps <= 0:
            max_steps = DEFAULT_MAX_STEPS

        # Keep cost consistent with current topology so selection can prune edges.
        _recompute_cost_inplace(genome)

        # Normalize components to ~[0,1]
        p = max(0.0, min(1.0, progress))
        c = float(getattr(genome, "cost", 0.0)) / float(MAX_COST)
        t = min(1.0, float(timesteps) / float(max_steps))


        fitness = (W_PROGRESS * p) - (W_COST * c) - (W_TIME * t)

        if fell:
            fitness -= PENALTY_FALL * (1.5 - p)
        elif crashed:
            fitness -= PENALTY_CRASH * (1.5 - p)
        elif stuck:
            fitness -= PENALTY_STUCK * (1.5 - p)

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