from __future__ import annotations

from dataclasses import dataclass
from typing import Any
import math
import random
import argparse

from genetic_algorithm.genome import Genome

@dataclass(frozen=True)
class _ParentEdge:
    edge_uuid: str
    from_uuid: str
    to_uuid: str
    material: str
    from_joint: dict[str, Any]
    to_joint: dict[str, Any]


def _as_parent_edge(edge: dict[str, Any], joints: list[dict[str, Any]]) -> _ParentEdge:
    edge_uuid = str(edge.get("uuid"))
    from_joint = joints[int(edge["from"])]
    to_joint = joints[int(edge["to"])]
    from_joint_uuid = str(from_joint.get("uuid"))
    to_joint_uuid = str(to_joint.get("uuid"))
    return _ParentEdge(
        edge_uuid=edge_uuid,
        from_uuid=from_joint_uuid,
        to_uuid=to_joint_uuid,
        material=str(edge.get("material", "ASPHALT")),
        from_joint=from_joint,
        to_joint=to_joint,
    )


def _nearest_joint_index(child_joints: list[dict[str, Any]], target_joint: dict[str, Any]) -> int | None:
    if not child_joints:
        return None

    target_x = float(target_joint["x"])
    target_y = float(target_joint["y"])
    best_joint_index = 0
    best_distance_sq = float("inf")
    for child_joint_index, child_joint in enumerate(child_joints):
        dx = float(child_joint["x"]) - target_x
        dy = float(child_joint["y"]) - target_y
        distance_sq = dx * dx + dy * dy
        if distance_sq < best_distance_sq:
            best_distance_sq = distance_sq
            best_joint_index = child_joint_index
    return best_joint_index



def crossover(parent1: Genome, parent2: Genome) -> Genome:
    # Order parents by fitness (fitter first)
    fitter_parent, other_parent = (parent1, parent2) if parent1.fitness >= parent2.fitness else (parent2, parent1)

    # ---- JOINTS: match by uuid ----
    fitter_joints_by_uuid = {str(j["uuid"]): j for j in fitter_parent.joints if j.get("uuid") is not None}
    other_joints_by_uuid = {str(j["uuid"]): j for j in other_parent.joints if j.get("uuid") is not None}

    child_joints: list[dict[str, Any]] = []
    joint_uuid_to_child_index: dict[str, int] = {}
    matching_joint_uuids = sorted(set(fitter_joints_by_uuid) & set(other_joints_by_uuid))
    fitter_unique_joint_uuids = sorted(set(fitter_joints_by_uuid) - set(other_joints_by_uuid))
    other_unique_joint_uuids = sorted(set(other_joints_by_uuid) - set(fitter_joints_by_uuid))

    #* Always keep matching joints; choose randomly from either parent.
    for joint_uuid in matching_joint_uuids:
        chosen_parent_joint = (
            fitter_joints_by_uuid[joint_uuid] if random.random() < 0.5 else other_joints_by_uuid[joint_uuid]
        )
        child_joint = Genome._normalize_joint_dict({**chosen_parent_joint, "uuid": joint_uuid})
        joint_uuid_to_child_index[joint_uuid] = len(child_joints)
        child_joints.append(child_joint)

    # Inherit unique joints primarily from fitter parent.
    # (Keep probability slightly <1 to avoid uncontrolled growth.)
    for joint_uuid in fitter_unique_joint_uuids:
        if random.random() < 0.85:
            child_joint = Genome._normalize_joint_dict({**fitter_joints_by_uuid[joint_uuid], "uuid": joint_uuid})
            joint_uuid_to_child_index[joint_uuid] = len(child_joints)
            child_joints.append(child_joint)

    # Rarely inherit unique joints from the less-fit parent.
    for joint_uuid in other_unique_joint_uuids:
        if random.random() < 0.15:
            child_joint = Genome._normalize_joint_dict({**other_joints_by_uuid[joint_uuid], "uuid": joint_uuid})
            joint_uuid_to_child_index[joint_uuid] = len(child_joints)
            child_joints.append(child_joint)

    # ---- EDGES: match by innovation id; snap endpoints if needed ----
    fitter_edges_by_uuid = {
        _as_parent_edge(e, fitter_parent.joints).edge_uuid: _as_parent_edge(e, fitter_parent.joints)
        for e in fitter_parent.edges
    }
    other_edges_by_uuid = {
        _as_parent_edge(e, other_parent.joints).edge_uuid: _as_parent_edge(e, other_parent.joints)
        for e in other_parent.edges
    }

    matching_edge_uuids = sorted(set(fitter_edges_by_uuid) & set(other_edges_by_uuid))
    fitter_unique_edge_uuids = sorted(set(fitter_edges_by_uuid) - set(other_edges_by_uuid))
    other_unique_edge_uuids = sorted(set(other_edges_by_uuid) - set(fitter_edges_by_uuid))

    child_edges: list[dict[str, Any]] = []
    edge_cache: set[tuple[int, int, str]] = set()

    def _try_add_edge(parent_edge: _ParentEdge) -> None:
        # Map or snap endpoints
        from_child_joint_index = joint_uuid_to_child_index.get(parent_edge.from_uuid)
        to_child_joint_index = joint_uuid_to_child_index.get(parent_edge.to_uuid)

        if from_child_joint_index is None:
            from_child_joint_index = _nearest_joint_index(child_joints, parent_edge.from_joint)
        if to_child_joint_index is None:
            to_child_joint_index = _nearest_joint_index(child_joints, parent_edge.to_joint)

        if from_child_joint_index is None or to_child_joint_index is None:
            return
        if from_child_joint_index == to_child_joint_index:
            return

        from_index, to_index = (
            (from_child_joint_index, to_child_joint_index)
            if from_child_joint_index <= to_child_joint_index
            else (to_child_joint_index, from_child_joint_index)
        )
        key = (from_index, to_index, parent_edge.material)
        if key in edge_cache:
            return

        # Length constraint (keep consistent with Genome.validate_bridge)
        dx = float(child_joints[from_index]["x"]) - float(child_joints[to_index]["x"])
        dy = float(child_joints[from_index]["y"]) - float(child_joints[to_index]["y"])
        if math.sqrt(dx * dx + dy * dy) > 12.5:
            return

        edge_cache.add(key)
        child_edges.append(
            {
                "from": from_index,
                "to": to_index,
                "material": parent_edge.material,
                "uuid": parent_edge.edge_uuid,
            }
        )

    # Matching edges: inherit from either parent.
    for edge_uuid in matching_edge_uuids:
        chosen_parent_edge = (
            fitter_edges_by_uuid[edge_uuid] if random.random() < 0.5 else other_edges_by_uuid[edge_uuid]
        )
        _try_add_edge(chosen_parent_edge)

    # Unique edges: primarily from fitter parent.
    for edge_uuid in fitter_unique_edge_uuids:
        if random.random() < 0.90:
            _try_add_edge(fitter_edges_by_uuid[edge_uuid])

    # Rare unique edges from less-fit parent.
    for edge_uuid in other_unique_edge_uuids:
        if random.random() < 0.10:
            _try_add_edge(other_edges_by_uuid[edge_uuid])

    child_bridge = dict(fitter_parent.bridge)
    child_bridge["joints"] = child_joints
    child_bridge["edges"] = child_edges

    child = Genome(bridge_manual=child_bridge)
    child.validate_bridge()
    return child


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Topological crossover smoke test.")
    parser.add_argument(
        "--parent-a",
        default="res/bridges/01.json",
        help="Path to parent A bridge JSON (default: res/bridges/01.json).",
    )
    parser.add_argument(
        "--parent-b",
        default="res/bridges/01_broken.json",
        help="Path to parent B bridge JSON (default: res/bridges/01_broken.json).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for reproducible crossover (default: 42).",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Optional output path for the child JSON (e.g. res/bridges/child.json).",
    )
    parser.add_argument(
        "--parent-a-fitness",
        type=float,
        default=10.0,
        help="Fitness assigned to parent A (default: 10.0).",
    )
    parser.add_argument(
        "--parent-b-fitness",
        type=float,
        default=5.0,
        help="Fitness assigned to parent B (default: 5.0).",
    )
    return parser


def main() -> int:
    args = _build_arg_parser().parse_args()
    random.seed(args.seed)

    parent_a = Genome(bridge_json_path=args.parent_a)
    parent_b = Genome(bridge_json_path=args.parent_b)

    parent_a.fitness = float(args.parent_a_fitness)
    parent_b.fitness = float(args.parent_b_fitness)

    child = crossover(parent_a, parent_b)

    print(
        f"parent_a path={args.parent_a} joints={len(parent_a.joints)} edges={len(parent_a.edges)} fitness={parent_a.fitness}\n"
        f"parent_b path={args.parent_b} joints={len(parent_b.joints)} edges={len(parent_b.edges)} fitness={parent_b.fitness}\n"
        f"child    joints={len(child.joints)} edges={len(child.edges)} valid={child.valid}"
    )

    if args.out:
        child.save_to_json(args.out)
        print(f"Wrote child to {args.out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())