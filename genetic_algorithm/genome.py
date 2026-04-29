import json
from copy import deepcopy
from typing import Any, Optional
import math

class Genome:
    def __init__(self, bridge_json_path: Optional[str] = None, bridge_manual: Optional[dict[str, Any]] = None):
        if bridge_json_path is None and bridge_manual is None:
            raise ValueError("Some bridge must be given for genome")
        
        if bridge_json_path is not None and bridge_manual is not None:
            raise ValueError("Only loaded or manual bridge may be provided")
        
        self.bridge_json_path = bridge_json_path

        if self.bridge_json_path is None:
            self.bridge = Genome.load_from_json(bridge_json_path)
        else:
            self.bridge = deepcopy(bridge_manual)

        self.joints = self.bridge['joints']
        self.edges = self.bridge['edges']
        self.fitness = 0.0
        self.progress = 0.0
        self.cost = self.bridge.get('cost', 0.0)
        self.valid = True


    @staticmethod
    def load_from_json(bridge_json_path: str):
        with open(bridge_json_path, 'r') as f:
            return json.load(f)
    

    def save_to_json(self, bridge_json_path: str):
        if bridge_json_path is not None:
            with open(bridge_json_path, 'w') as f:
                json.dump(self.bridge, f, indent=2)

    def clone(self):
        new_genome = Genome(bridge_manual=deepcopy(self.bridge))

        new_genome.fitness = self.fitness
        new_genome.progress = self.progress
        new_genome.cost = self.cost
        new_genome.valid = self.valid

        return new_genome
    
    def validate_bridge(self):
        num_joints = len(self.joints)

        for edge in self.edges:
            if edge['from'] < 0 or edge['from'] >= num_joints:
                self.valid = False
                return False
            if edge['to'] < 0 or edge['to'] >= num_joints:
                self.valid = False
                return False
            if edge['from'] == edge['to']:
                self.valid = False
                return False
            
        edge_cache = set()
        for edge in self.edges:
            # (to -> from) is same as (from -> to)
            j1, j2 = sorted((edge['from'], edge['to']))
            edge_key = (j1, j2)

            if edge_key in edge_cache:
                self.valid = False
                return False
            
            edge_cache.add(edge_key)

        for edge in self.edges:
            if self.edge_length(edge['from'], edge['to']) > 12.5:
                self.valid = False
                return False
    
        self.valid = True
        return True
    
    def fixed_joints(self):
        fixed_joint_indices = []
        for i, joint in enumerate(self.joints):
            if joint.get('fixed', False):
                fixed_joint_indices.append(i)
        
        return fixed_joint_indices
    
    def mutable_joints(self):
        mutable_joint_indices = []
        for i, joint in enumerate(self.joints):
            if not joint.get('fixed', False):
                mutable_joint_indices.append(i)
        
        return mutable_joint_indices
    
    # euclidean distance between joints
    def edge_length(self, i, j):
        dx = self.joints[i]['x'] - self.joints[j]['x']
        dy = self.joints[i]['y'] - self.joints[j]['y']

        return math.sqrt(dx*dx + dy*dy)
