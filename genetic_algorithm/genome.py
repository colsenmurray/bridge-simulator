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
        self.end_reason = None

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
        if "from_uuid" in edge and edge["from_uuid"] is not None:
            normalized["from_uuid"] = str(edge["from_uuid"])
        if "to_uuid" in edge and edge["to_uuid"] is not None:
            normalized["to_uuid"] = str(edge["to_uuid"])
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

        # Ensure every edge has from_uuid/to_uuid derived from endpoints.
        for e in edges:
            from_idx = int(e["from"])
            to_idx = int(e["to"])
            if not (0 <= from_idx < len(joints) and 0 <= to_idx < len(joints)):
                raise ValueError("Edge references invalid joint index while deriving from_uuid/to_uuid")
            e["from_uuid"] = str(joints[from_idx]["uuid"])
            e["to_uuid"] = str(joints[to_idx]["uuid"])

    @staticmethod
    def _reindex_edges_from_uuids_inplace(bridge: dict[str, Any]) -> None:
        joints: list[dict[str, Any]] = bridge.get("joints", [])
        edges: list[dict[str, Any]] = bridge.get("edges", [])

        uuid_to_index: dict[str, int] = {}
        for i, j in enumerate(joints):
            if j.get("uuid") is None:
                continue
            uuid_to_index[str(j["uuid"])] = i

        kept: list[dict[str, Any]] = []
        for e in edges:
            fu = e.get("from_uuid")
            tu = e.get("to_uuid")
            if fu is None or tu is None:
                continue
            from_index = uuid_to_index.get(str(fu))
            to_index = uuid_to_index.get(str(tu))
            if from_index is None or to_index is None:
                continue
            e["from"] = int(from_index)
            e["to"] = int(to_index)
            kept.append(e)

        bridge["edges"] = kept

    @staticmethod
    def prune_components_without_fixed_anchor_inplace(bridge: dict[str, Any]) -> None:
        """
        Prune any connected component (by edges) that does not contain a fixed joint.
        Uses disjoint sets (union-find) over joint indices.
        """
        joints: list[dict[str, Any]] = bridge.get("joints", [])
        edges: list[dict[str, Any]] = bridge.get("edges", [])
        n = len(joints)
        if n == 0:
            bridge["edges"] = []
            return

        parent = list(range(n))
        rank = [0] * n

        def find(x: int) -> int:
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(a: int, b: int) -> None:
            ra = find(a)
            rb = find(b)
            if ra == rb:
                return
            if rank[ra] < rank[rb]:
                parent[ra] = rb
            elif rank[ra] > rank[rb]:
                parent[rb] = ra
            else:
                parent[rb] = ra
                rank[ra] += 1

        for e in edges:
            try:
                a = int(e["from"])
                b = int(e["to"])
            except (KeyError, ValueError, TypeError):
                continue
            if 0 <= a < n and 0 <= b < n and a != b:
                union(a, b)

        keep_roots: set[int] = set()
        for i, j in enumerate(joints):
            if bool(j.get("fixed", False)):
                keep_roots.add(find(i))

        # If there are no fixed joints, keep everything (nothing to anchor to).
        if not keep_roots:
            return

        keep_joint_mask = [find(i) in keep_roots for i in range(n)]
        old_to_new: dict[int, int] = {}
        new_joints: list[dict[str, Any]] = []
        for old_i, keep in enumerate(keep_joint_mask):
            if not keep:
                continue
            old_to_new[old_i] = len(new_joints)
            new_joints.append(joints[old_i])

        new_edges: list[dict[str, Any]] = []
        for e in edges:
            try:
                a_old = int(e["from"])
                b_old = int(e["to"])
            except (KeyError, ValueError, TypeError):
                continue
            a_new = old_to_new.get(a_old)
            b_new = old_to_new.get(b_old)
            if a_new is None or b_new is None or a_new == b_new:
                continue
            e["from"] = int(a_new)
            e["to"] = int(b_new)
            new_edges.append(e)

        bridge["joints"] = new_joints
        bridge["edges"] = new_edges

        # Refresh endpoint uuids to stay consistent with indices.
        Genome._ensure_uuids_inplace(bridge)
        Genome._normalize_bridge_inplace(bridge)

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
        new_genome.end_reason = self.end_reason

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
