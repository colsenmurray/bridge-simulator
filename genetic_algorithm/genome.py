import json
from typing import Any

class Genome:
    def __init__(self, bridge_json_path: str):
        self.bridge_json_path = bridge_json_path
        self.bridge = Genome.load_from_json(bridge_json_path)
        self.joints = self.bridge['joints']
        self.edges = self.bridge['edges']
        self.fitness = 0

    @staticmethod
    def load_from_json(bridge_json_path: str):
        with open(bridge_json_path, 'r') as f:
            return json.load(f)
    

    def save_to_json(self, bridge_json_path: str):
        with open(bridge_json_path, 'w') as f:
            json.dump(self.bridge, f, indent=2)

    