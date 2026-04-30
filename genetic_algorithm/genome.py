import json
from copy import deepcopy
from typing import Any, Optional
import math
import uuid
from collections import deque

class Genome:
    def __init__(self, bridge_json_path: Optional[str] = None, bridge_manual: Optional[dict[str, Any]] = None):
        if bridge_json_path is None and bridge_manual is None:
            raise ValueError("Some bridge must be given for genome")
        
        if bridge_json_path is not None and bridge_manual is not None:
            raise ValueError("Only loaded or manual bridge may be provided")
        
        self.bridge_json_path = bridge_json_path

        if self.bridge_json_path is not None:
            self.bridge = Genome.load_from_json(bridge_json_path)
        else:
            self.bridge = deepcopy(bridge_manual)

        # Keep joints/edges as dicts; other modules (mutation/fitness/validation)
        # operate on this JSON shape directly.
        self.joints: list[dict[str, Any]] = self.bridge.get("joints", [])
        self.edges: list[dict[str, Any]] = self.bridge.get("edges", [])
        Genome._normalize_bridge_inplace(self.bridge)
        # Ensure every joint and edge has a stable UUID string. Existing uuids
        # from loaded files are preserved; missing ones are filled (legacy).
        Genome._ensure_uuids_inplace(self.bridge)
        self.fitness = 0.0
        self.progress = 0.0
        self.cost = self.bridge.get('cost', 0.0)
        self.valid = True


    @staticmethod
    def _normalize_joint_dict(joint: dict[str, Any]) -> dict[str, Any]:
        """
        Keep JSON field order compatible with the game's regex-based loader:
        x, y, fixed, ...optional GA fields...
        """
        normalized: dict[str, Any] = {
            "x": float(joint["x"]),
            "y": float(joint["y"]),
            "fixed": bool(joint.get("fixed", False)),
        }
        if "uuid" in joint and joint["uuid"] is not None:
            normalized["uuid"] = str(joint["uuid"])
        return normalized

    @staticmethod
    def _normalize_edge_dict(edge: dict[str, Any]) -> dict[str, Any]:
        normalized: dict[str, Any] = {
            "from": int(edge["from"]),
            "to": int(edge["to"]),
            "material": str(edge.get("material", "ASPHALT")),
        }
        if "uuid" in edge and edge["uuid"] is not None:
            normalized["uuid"] = str(edge["uuid"])
        return normalized

    @staticmethod
    def _normalize_bridge_inplace(bridge: dict[str, Any]) -> None:
        joints = bridge.get("joints", [])
        for i in range(len(joints)):
            joints[i] = Genome._normalize_joint_dict(joints[i])

        edges = bridge.get("edges", [])
        for i in range(len(edges)):
            edges[i] = Genome._normalize_edge_dict(edges[i])

    @staticmethod
    def _ensure_uuids_inplace(bridge: dict[str, Any]) -> None:
        """
        Ensure every joint and edge has a UUID string.

        - Existing uuids are preserved (loaded bridges).
        - Missing uuids are filled with random UUIDv4 strings (legacy bridges,
          or newly created bridges if caller forgot to include them).
        """
        joints: list[dict[str, Any]] = bridge.get("joints", [])
        edges: list[dict[str, Any]] = bridge.get("edges", [])

        for j in joints:
            if j.get("uuid") is None:
                j["uuid"] = str(uuid.uuid4())
            else:
                j["uuid"] = str(j["uuid"])

        for e in edges:
            if e.get("uuid") is None:
                e["uuid"] = str(uuid.uuid4())
            else:
                e["uuid"] = str(e["uuid"])

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

        # Connectivity constraint: must have a path from leftmost fixed joint
        # to rightmost fixed joint.
        fixed = [i for i, j in enumerate(self.joints) if j.get("fixed", False)]
        if len(fixed) >= 2:
            left = min(fixed, key=lambda i: float(self.joints[i]["x"]))
            right = max(fixed, key=lambda i: float(self.joints[i]["x"]))

            adj: list[list[int]] = [[] for _ in range(num_joints)]
            for e in self.edges:
                a = int(e["from"])
                b = int(e["to"])
                if 0 <= a < num_joints and 0 <= b < num_joints and a != b:
                    adj[a].append(b)
                    adj[b].append(a)

            q = deque([left])
            seen = {left}
            while q:
                u = q.popleft()
                if u == right:
                    break
                for v in adj[u]:
                    if v not in seen:
                        seen.add(v)
                        q.append(v)
            else:
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
