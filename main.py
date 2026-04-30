import argparse
from genetic_algorithm.config import GAConfig
from genetic_algorithm.evolution import Evolution


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument("--config_file", type=str, required=True)
    parser.add_argument("--output_folder", type=str, required=True)
    args = parser.parse_args()


    config = GAConfig.load_from_yaml(args.config_file)

    evolution = Evolution(config, args.output_folder)
    evolution.run()