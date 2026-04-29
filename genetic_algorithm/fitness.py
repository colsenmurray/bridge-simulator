from genome import Genome
import tempfile
import json
import subprocess
import os

def evaluate(genome: Genome, cost_penalty: float = 0.001):
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
            [EXEC_PATH, '--level', '01', '--bridge', temp_path, '--max-steps', '500'],
            capture_output=True,
            text=True
        )

        output_path = result.stdout.strip()

        if not output_path.endswith(".json"):
            raise RuntimeError(f"Unexpected simulation output: {output_path}")

        with open(output_path, "r") as f:
            output = json.load(f)

        progress = output['samples'][-1]['progress']

        fitness = progress - cost_penalty * genome.cost

        genome.progress = progress
        genome.fitness = fitness

        return fitness
    
    finally:
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)

        if output_path and os.path.exists(output_path):
            os.remove(output_path)
