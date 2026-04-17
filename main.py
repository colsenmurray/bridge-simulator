import argparse
import json
from genetic_algorithm.config import GAConfig
from genetic_algorithm.evolution import Evolution
import yaml

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bridge_folder", type=str, required=True)
    parser.add_argument("--config_file", type=str, required=True)
    parser.add_argument("--output_folder", type=str, required=True)
    args = parser.parse_args()

    with open(args.config_file, 'r') as f:
        config = yaml.load(f, Loader=yaml.FullLoader)

    config = GAConfig(**config)

    evolution = Evolution(config)
    evolution.run()