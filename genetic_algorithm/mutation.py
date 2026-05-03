from collections import deque
from genetic_algorithm.genome import Genome
from genetic_algorithm.terrain import joint_in_terrain, repair_mutable_joints_out_of_terrain
import random
import math
import uuid
import numpy as np

MAX_EDGE_LEN = 8
# Maximum edges incident to one joint (mutation will not add an edge that would exceed this).
MAX_EDGES_PER_JOINT = 6
# New joints: reject proposed (x, y) if too many existing joints already lie in this radius.
NODE_DENSITY_RADIUS = 4.0
# If more than this many existing joints fall within NODE_DENSITY_RADIUS of the proposal, resample.
MAX_JOINTS_IN_DENSITY_RADIUS = 4
NEW_JOINT_DENSITY_MAX_ATTEMPTS = 24
# Resample joint moves that would land inside terrain (see res/terrain/<level>.json).
MOVE_JOINT_TERRAIN_MAX_ATTEMPTS = 12
# At this fail streak (and above), joint picks are fully weighted toward large hop distance from fixed.
TOPO_BIAS_FULL_AT_STREAK = 25
# Weight ~ (hops + 1) ** TOPO_WEIGHT_POWER when topology bias is active.
TOPO_WEIGHT_POWER = 2.0
# _add_edge: try adding a new mobile joint vs only drawing an edge between two existing joints.
ADD_EDGE_NEW_JOINT_PROB = 0.5
# When adding a new joint, prefer placement that immediately gets two edges (two parents).
NEW_JOINT_DUAL_PROB = 0.88

MATERIALS = np.array(["ASPHALT", "STEEL", "WOOD"])
W_MATERIAL = np.array([3.0, 1.0, 1.0])
# Probability to mutate an existing edge's material each mutate() call.
EDGE_MATERIAL_MUTATION_PROB = 0.1


def _mutate_edge_material(genome: Genome) -> None:
    """
    Pick a random edge and change its material (biased by W_MATERIAL).
    Keeps endpoints; only changes material + uuid.
    """
    if not genome.edges:
        return

    # Try a few times to avoid "no-op" (picking same material again).
    for _ in range(8):
        e_idx = random.randrange(len(genome.edges))
        edge = genome.edges[e_idx]
        current = str(edge.get("material", "ASPHALT")).upper()

        weights = np.array(W_MATERIAL, dtype=float)
        if weights.size != MATERIALS.size:
            # Defensive: fall back to uniform if misconfigured.
            weights = np.ones_like(MATERIALS, dtype=float)
        weights = np.maximum(weights, 0.0)
        if float(weights.sum()) <= 0.0:
            weights = np.ones_like(MATERIALS, dtype=float)

        # Exclude the current material to guarantee a change when possible.
        mask = MATERIALS != current
        choices = MATERIALS[mask]
        if choices.size == 0:
            return
        p = weights[mask] / float(weights[mask].sum())
        new_material = str(np.random.choice(choices, p=p))

        if new_material == current:
            continue

        edge["material"] = new_material
        edge["uuid"] = str(uuid.uuid4())
        genome.edges[e_idx] = Genome._normalize_edge_dict(edge)
        return


def _connected_components_and_centroids(
    genome: Genome,
) -> tuple[list[int], dict[int, tuple[float, float]], dict[int, list[int]]]:
    """
    Compute disjoint sets (connected components) over joints using current edges.

    An isolated fixed joint (``fixed`` and no incident edges) is always its own
    component; connected joints are grouped by reachability as usual.

    Returns:
    - comp_id: list[int] where comp_id[i] is the component id for joint i
    - centroid_by_comp: {comp: (cx, cy)} bias reference per component — mean position of
      **fixed** joints in that component when any exist (so opposite anchors are targets
      from the start); otherwise the geometric centroid of all members.
    - members_by_comp: {comp: [joint indices...]}
    """
    n = len(genome.joints)
    if n == 0:
        return ([], {}, {})

    adj: list[list[int]] = [[] for _ in range(n)]
    for e in genome.edges:
        try:
            a = int(e["from"])
            b = int(e["to"])
        except (KeyError, ValueError, TypeError):
            continue
        if not (0 <= a < n and 0 <= b < n) or a == b:
            continue
        adj[a].append(b)
        adj[b].append(a)

    comp_id = [-1] * n
    members_by_comp: dict[int, list[int]] = {}
    comp = 0

    # Isolated fixed anchors: own disjoint set (singleton), never merged with others.
    for i in range(n):
        if bool(genome.joints[i].get("fixed", False)) and not adj[i]:
            comp_id[i] = comp
            members_by_comp[comp] = [i]
            comp += 1

    for i in range(n):
        if comp_id[i] != -1:
            continue
        q: deque[int] = deque([i])
        comp_id[i] = comp
        members: list[int] = []
        while q:
            u = q.popleft()
            members.append(u)
            for v in adj[u]:
                if comp_id[v] == -1:
                    comp_id[v] = comp
                    q.append(v)
        members_by_comp[comp] = members
        comp += 1

    centroid_by_comp: dict[int, tuple[float, float]] = {}
    for c, members in members_by_comp.items():
        fixed_js = [j for j in members if bool(genome.joints[j].get("fixed", False))]
        use = fixed_js if fixed_js else members
        sx = 0.0
        sy = 0.0
        for j in use:
            sx += float(genome.joints[j]["x"])
            sy += float(genome.joints[j]["y"])
        inv = 1.0 / float(len(use)) if use else 1.0
        centroid_by_comp[c] = (sx * inv, sy * inv)

    return (comp_id, centroid_by_comp, members_by_comp)


def _pick_other_component_centroid(
    genome: Genome,
    comp_id: list[int],
    centroid_by_comp: dict[int, tuple[float, float]],
    members_by_comp: dict[int, list[int]],
    base_joint_index: int,
) -> tuple[float, float] | None:
    """
    Pick a cross-component bias target (usually another anchor) for base_joint_index.

    When several other components exist, prefers one that contains a fixed joint so
    early growth steers toward opposite banks / anchors, not only floating debris.
    """
    if not comp_id or base_joint_index < 0 or base_joint_index >= len(comp_id):
        return None
    base_c = comp_id[base_joint_index]
    other = [c for c in centroid_by_comp.keys() if c != base_c]
    if not other:
        return None
    with_anchor = [
        c
        for c in other
        if any(bool(genome.joints[j].get("fixed", False)) for j in members_by_comp.get(c, []))
    ]
    pick_from = with_anchor if with_anchor else other
    return centroid_by_comp[random.choice(pick_from)]


def _shortest_hops_to_nearest_fixed(genome: Genome) -> list[int]:
    """
    For each joint index, graph distance (number of edges) along the bridge graph to the
    nearest fixed joint. Unreachable joints get max_hops + 1.
    """
    n = len(genome.joints)
    if n == 0:
        return []

    adj: list[list[int]] = [[] for _ in range(n)]
    for e in genome.edges:
        try:
            a = int(e["from"])
            b = int(e["to"])
        except (KeyError, ValueError, TypeError):
            continue
        if not (0 <= a < n and 0 <= b < n) or a == b:
            continue
        adj[a].append(b)
        adj[b].append(a)

    fixed = [i for i in range(n) if genome.joints[i].get("fixed", False)]
    dist = [-1] * n
    if not fixed:
        return [0] * n

    q: deque[int] = deque()
    for i in fixed:
        dist[i] = 0
        q.append(i)
    while q:
        u = q.popleft()
        for v in adj[u]:
            if dist[v] == -1:
                dist[v] = dist[u] + 1
                q.append(v)

    known = [d for d in dist if d >= 0]
    max_h = max(known) if known else 0
    for i in range(n):
        if dist[i] < 0:
            dist[i] = max_h + 1
    return dist


def _topology_bias_strength(fail_streak: int) -> float:
    """Blend factor in [0, 1]: 0 = uniform joint choice, 1 = strong bias to high-hop joints."""
    if fail_streak <= 0:
        return 0.0
    return min(1.0, float(fail_streak) / float(TOPO_BIAS_FULL_AT_STREAK))


def _pick_joint_index_from_candidates(
    genome: Genome,
    candidates: list[int],
    fail_streak: int,
    hop_dist: list[int],
    *,
    topo_bias_enabled: bool = True,
) -> int:
    """Pick one index from candidates; as fail_streak grows, prefer larger hop_dist[j]."""
    if not candidates:
        raise ValueError("candidates must be non-empty")
    if len(candidates) == 1:
        return candidates[0]

    if not topo_bias_enabled:
        return random.choice(candidates)

    t = _topology_bias_strength(fail_streak)
    if t <= 0.0:
        return random.choice(candidates)

    weights: list[float] = []
    for j in candidates:
        h = float(hop_dist[j])
        topo = (h + 1.0) ** TOPO_WEIGHT_POWER
        w = (1.0 - t) * 1.0 + t * topo
        weights.append(w)

    total = sum(weights)
    r = random.random() * total
    acc = 0.0
    for j, w in zip(candidates, weights):
        acc += w
        if r <= acc:
            return j
    return candidates[-1]


def _edge_key_by_joint_uuid(
    genome: Genome,
    joint_index_a: int,
    joint_index_b: int,
    material: str,
) -> tuple[str, str, str] | None:
    """
    Return a stable duplicate-check key for an edge based on joint uuids.
    If innovations is None and joints have no uuids, returns None.
    """
    joint_a = genome.joints[joint_index_a]
    joint_b = genome.joints[joint_index_b]

    joint_a_uuid = joint_a.get("uuid")
    joint_b_uuid = joint_b.get("uuid")

    if joint_a_uuid is None or joint_b_uuid is None:
        return None

    a, b = (str(joint_a_uuid), str(joint_b_uuid))
    lo, hi = (a, b) if a <= b else (b, a)
    return (lo, hi, str(material))


def _edge_already_exists(
    genome: Genome,
    joint_index_a: int,
    joint_index_b: int,
    material: str,
) -> bool:
    """
    Duplicate check:
    - always prevent duplicates by indices (cheap, works for legacy genomes)
    - if uuids are available, also prevent duplicates by joint-uuid pair + material
    """
    a_index, b_index = (
        (joint_index_a, joint_index_b)
        if joint_index_a <= joint_index_b
        else (joint_index_b, joint_index_a)
    )

    # Index-based duplicate check (existing behavior)
    for edge in genome.edges:
        edge_a = min(edge["from"], edge["to"])
        edge_b = max(edge["from"], edge["to"])
        if (a_index, b_index) == (edge_a, edge_b) and str(edge.get("material", "ASPHALT")) == str(material):
            return True

    # UUID-based duplicate check (more robust across reindexing)
    proposed_key = _edge_key_by_joint_uuid(genome, a_index, b_index, material)
    if proposed_key is None:
        return False

    for edge in genome.edges:
        existing_material = str(edge.get("material", "ASPHALT"))
        edge_key = _edge_key_by_joint_uuid(genome, int(edge["from"]), int(edge["to"]), existing_material)
        if edge_key == proposed_key:
            return True

    return False


def _incident_degree(genome: Genome, joint_index: int) -> int:
    """Count edges touching joint_index (undirected degree)."""
    n = len(genome.joints)
    if not (0 <= joint_index < n):
        return 0
    d = 0
    for e in genome.edges:
        try:
            a = int(e["from"])
            b = int(e["to"])
        except (KeyError, ValueError, TypeError):
            continue
        if a == joint_index or b == joint_index:
            d += 1
    return d


def _count_joints_within_radius(
    genome: Genome,
    cx: float,
    cy: float,
    radius: float,
    *,
    exclude: set[int] | frozenset | None = None,
) -> int:
    """How many joints lie within radius of (cx, cy), excluding attachment indices if given."""
    r2 = radius * radius
    skip = exclude if exclude is not None else frozenset()
    n = 0
    for j, joint in enumerate(genome.joints):
        if j in skip:
            continue
        dx = float(joint["x"]) - cx
        dy = float(joint["y"]) - cy
        if dx * dx + dy * dy <= r2:
            n += 1
    return n


# shift mutable joint
def _move_joint(genome: Genome, sigma: float = 1.0, fail_streak: int = 0, level: str | None = None):
    indices = genome.mutable_joints()
    if not indices:
        return

    _comp_id, _centroid_by_comp, _members = _connected_components_and_centroids(genome)
    topo_bias_enabled = len(_centroid_by_comp) > 1

    hop = _shortest_hops_to_nearest_fixed(genome)
    i = _pick_joint_index_from_candidates(
        genome, indices, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
    )
    joint = genome.joints[i]

    base_x = float(joint["x"])
    base_y = float(joint["y"])
    for _ in range(MOVE_JOINT_TERRAIN_MAX_ATTEMPTS):
        nx = base_x + random.uniform(-sigma, sigma)
        ny = base_y + random.uniform(-sigma, sigma)
        if not joint_in_terrain(level, nx, ny):
            joint["x"] = nx
            joint["y"] = ny
            return


# add new edge from existing joint
def _add_edge(genome: Genome, fail_streak: int = 0, level: str | None = None):
    num_joints = len(genome.joints)

    if num_joints == 0:
        return

    comp_id, centroid_by_comp, members_by_comp = _connected_components_and_centroids(genome)
    topo_bias_enabled = len(centroid_by_comp) > 1

    hop = _shortest_hops_to_nearest_fixed(genome)
    eligible_i = [
        idx for idx in range(num_joints) if _incident_degree(genome, idx) < MAX_EDGES_PER_JOINT
    ]
    if not eligible_i:
        return

    p = W_MATERIAL / W_MATERIAL.sum()
    material = np.random.choice(MATERIALS, p=p)


    try_new_joint = random.random() < ADD_EDGE_NEW_JOINT_PROB

    # --- New mobile joint: usually two edges to two parents (dual), else one edge from one parent ---
    if try_new_joint:
        want_dual = len(eligible_i) >= 2 and random.random() < NEW_JOINT_DUAL_PROB

        if want_dual:
            min_leg = 1.0
            two_r = 2.0 * MAX_EDGE_LEN

            def _resample_or_stop_dual() -> bool:
                """True = try another outer draw; False = give up on dual for this attempt."""
                return random.random() < 0.5

            for _ in range(NEW_JOINT_DENSITY_MAX_ATTEMPTS):
                a = _pick_joint_index_from_candidates(
                    genome, eligible_i, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
                )
                partners = [
                    j
                    for j in eligible_i
                    if j != a and genome.edge_length(a, j) <= two_r
                ]
                if not partners:
                    if _resample_or_stop_dual():
                        continue
                    break
                # Prefer second parent from a different component, if possible.
                partners_other = [j for j in partners if comp_id and comp_id[j] != comp_id[a]]
                b_candidates = partners_other if partners_other else partners
                b = _pick_joint_index_from_candidates(
                    genome, b_candidates, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
                )

                ax = float(genome.joints[a]["x"])
                ay = float(genome.joints[a]["y"])
                bx = float(genome.joints[b]["x"])
                by = float(genome.joints[b]["y"])
                d_ab = genome.edge_length(a, b)
                if d_ab > two_r or d_ab < 1e-6:
                    if _resample_or_stop_dual():
                        continue
                    break

                dx = bx - ax
                dy = by - ay
                ux = dx / d_ab
                uy = dy / d_ab
                mx = ax + 0.5 * d_ab * ux
                my = ay + 0.5 * d_ab * uy
                px = -uy
                py = ux
                h_sq = MAX_EDGE_LEN * MAX_EDGE_LEN - (0.5 * d_ab) * (0.5 * d_ab)
                if h_sq < 0.0:
                    if _resample_or_stop_dual():
                        continue
                    break
                h = math.sqrt(h_sq)

                # Bias placement toward another component centroid (if any exist).
                target_centroid = _pick_other_component_centroid(
                    genome, comp_id, centroid_by_comp, members_by_comp, a
                )
                best_xy: tuple[float, float] | None = None
                best_score = -1.0e30

                for _ in range(24):
                    if h < 1e-9:
                        t = 0.0
                    else:
                        t = random.uniform(-h, h)
                    new_x = mx + t * px
                    new_y = my + t * py
                    la = math.hypot(new_x - ax, new_y - ay)
                    lb = math.hypot(new_x - bx, new_y - by)
                    if la < min_leg or lb < min_leg or la > MAX_EDGE_LEN or lb > MAX_EDGE_LEN:
                        continue
                    nearby = _count_joints_within_radius(
                        genome, new_x, new_y, NODE_DENSITY_RADIUS, exclude={a, b}
                    )
                    if nearby > MAX_JOINTS_IN_DENSITY_RADIUS:
                        continue
                    if joint_in_terrain(level, new_x, new_y):
                        continue

                    # Score: prefer being closer to another component centroid when available.
                    score = 0.0
                    if target_centroid is not None:
                        cx, cy = target_centroid
                        d = math.hypot(new_x - cx, new_y - cy)
                        score += -d
                    else:
                        # If no other components exist, slightly prefer moderate leg lengths.
                        score += -abs((la + lb) - (0.7 * MAX_EDGE_LEN))

                    if score > best_score:
                        best_score = score
                        best_xy = (new_x, new_y)

                if best_xy is not None:
                    new_x, new_y = best_xy
                    new_index = len(genome.joints)
                    new_joint = Genome._normalize_joint_dict(
                        {
                            "x": new_x,
                            "y": new_y,
                            "fixed": False,
                            "uuid": str(uuid.uuid4()),
                        }
                    )
                    genome.joints.append(new_joint)

                    for u, v in ((a, new_index), (b, new_index)):
                        e = {
                            "from": u,
                            "to": v,
                            "material": material,
                            "uuid": str(uuid.uuid4()),
                            "from_uuid": str(genome.joints[u]["uuid"]),
                            "to_uuid": str(genome.joints[v]["uuid"]),
                        }
                        genome.edges.append(e)
                        genome.edges[-1] = Genome._normalize_edge_dict(e)
                    return

                if _resample_or_stop_dual():
                    continue
                break

        # One beam from one parent (also fallback if dual was skipped or failed)
        for _ in range(NEW_JOINT_DENSITY_MAX_ATTEMPTS):
            i = _pick_joint_index_from_candidates(
                genome, eligible_i, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
            )
            ix = float(genome.joints[i]["x"])
            iy = float(genome.joints[i]["y"])
            target_centroid = _pick_other_component_centroid(
                genome, comp_id, centroid_by_comp, members_by_comp, i
            )
            for __ in range(16):
                if target_centroid is None:
                    angle = random.uniform(0, 2 * math.pi)
                else:
                    cx, cy = target_centroid
                    base_angle = math.atan2(cy - iy, cx - ix)
                    # Tight-ish bias cone toward other component centroid, with rare exploration.
                    if random.random() < 0.15:
                        angle = random.uniform(0, 2 * math.pi)
                    else:
                        angle = random.gauss(base_angle, math.pi / 7.0)
                dist = random.uniform(1.0, MAX_EDGE_LEN)
                new_x = ix + (dist * math.cos(angle))
                new_y = iy + (dist * math.sin(angle))
                nearby = _count_joints_within_radius(
                    genome, new_x, new_y, NODE_DENSITY_RADIUS, exclude={i}
                )
                if nearby > MAX_JOINTS_IN_DENSITY_RADIUS:
                    continue
                if joint_in_terrain(level, new_x, new_y):
                    continue

                new_index = len(genome.joints)
                new_joint = Genome._normalize_joint_dict(
                    {
                        "x": new_x,
                        "y": new_y,
                        "fixed": False,
                        "uuid": str(uuid.uuid4()),
                    }
                )
                genome.joints.append(new_joint)
                new_edge = {
                    "from": i,
                    "to": new_index,
                    "material": material,
                    "uuid": str(uuid.uuid4()),
                    "from_uuid": str(genome.joints[i]["uuid"]),
                    "to_uuid": str(new_joint["uuid"]),
                }
                genome.edges.append(new_edge)
                genome.edges[-1] = Genome._normalize_edge_dict(new_edge)
                return
        return

    # --- Only add an edge between two existing joints ---
    if num_joints <= 1:
        return

    i = _pick_joint_index_from_candidates(
        genome, eligible_i, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
    )
    valid_j: list[int] = []
    for j in range(num_joints):
        if j == i:
            continue
        if _incident_degree(genome, j) >= MAX_EDGES_PER_JOINT:
            continue
        if genome.edge_length(i, j) > MAX_EDGE_LEN:
            continue
        if _edge_already_exists(genome, i, j, material):
            continue
        valid_j.append(j)

    if valid_j:
        # If any joints in-range belong to a different component, prefer those.
        other_comp = (
            [j for j in valid_j if comp_id and comp_id[j] != comp_id[i]] if comp_id else []
        )
        j_candidates = other_comp if other_comp else valid_j
        j = _pick_joint_index_from_candidates(
            genome, j_candidates, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
        )
        new_edge = {
            "from": i,
            "to": j,
            "material": material,
            "uuid": str(uuid.uuid4()),
            "from_uuid": str(genome.joints[i]["uuid"]),
            "to_uuid": str(genome.joints[j]["uuid"]),
        }
        genome.edges.append(new_edge)
        genome.edges[-1] = Genome._normalize_edge_dict(new_edge)
        return

    for _ in range(10):
        j = random.randrange(num_joints)
        if j == i:
            continue
        if _incident_degree(genome, j) >= MAX_EDGES_PER_JOINT:
            continue
        if genome.edge_length(i, j) > MAX_EDGE_LEN:
            continue
        if _edge_already_exists(genome, i, j, material):
            continue
        new_edge = {
            "from": i,
            "to": j,
            "material": material,
            "uuid": str(uuid.uuid4()),
            "from_uuid": str(genome.joints[i]["uuid"]),
            "to_uuid": str(genome.joints[j]["uuid"]),
        }
        genome.edges.append(new_edge)
        genome.edges[-1] = Genome._normalize_edge_dict(new_edge)
        return


# remove random edge
def _remove_edge(genome: Genome):
    if not genome.edges:
        return
    
    num_edges = len(genome.edges)
    i = random.randrange(num_edges)

    genome.edges.pop(i)


def _remove_joint(genome: Genome, fail_streak: int = 0) -> None:
    mutable = genome.mutable_joints()
    if not mutable:
        return

    _comp_id, _centroid_by_comp, _members = _connected_components_and_centroids(genome)
    topo_bias_enabled = len(_centroid_by_comp) > 1

    hop = _shortest_hops_to_nearest_fixed(genome)
    remove_index = _pick_joint_index_from_candidates(
        genome, mutable, fail_streak, hop, topo_bias_enabled=topo_bias_enabled
    )
    removed_uuid = str(genome.joints[remove_index].get("uuid"))

    # Remove joint
    genome.joints.pop(remove_index)

    # Remove edges incident to the removed joint (by uuid endpoints)
    kept_edges: list[dict] = []
    for e in genome.edges:
        if str(e.get("from_uuid")) == removed_uuid or str(e.get("to_uuid")) == removed_uuid:
            continue
        kept_edges.append(e)
    genome.bridge["edges"] = kept_edges
    genome.edges = genome.bridge["edges"]

    # Reindex edges based on their UUID endpoints so indices match shifted joints.
    genome.bridge["joints"] = genome.joints
    Genome._reindex_edges_from_uuids_inplace(genome.bridge)
    genome.edges = genome.bridge["edges"]

    # Normalize edge dicts after reindexing
    for i in range(len(genome.edges)):
        genome.edges[i] = Genome._normalize_edge_dict(genome.edges[i])


# clone genome, mutate it, return new genome
def mutate(parent: Genome, fail_streak: int = 0, level: str | None = None):
    child = parent.clone()

    # Small continuous tweak
    if random.random() < 0.70:
        _move_joint(child, fail_streak=fail_streak, level=level)

    # r = random.random()
    # if r < 0.1:
    #     _remove_joint(child)
    # elif r < 0.2:
    #     _add_edge(child)
    # elif r < 0.3:
    #     _remove_edge(child)

    # Joint-structure mutation
    r_joint = random.random()

    if r_joint < max(0.1, 0.3 - (fail_streak * 0.01)):
        _remove_joint(child, fail_streak)

    # Edge-structure mutation
    r_edge = random.random()
    if r_edge < min(0.9, 0.3 + (fail_streak * 0.01)):
        _add_edge(child, fail_streak, level=level)

    if r_edge < max(0.1, 0.55 - (fail_streak * 0.01)):
        _remove_edge(child)

    # Material mutation (doesn't change topology)
    if random.random() < EDGE_MATERIAL_MUTATION_PROB:
        _mutate_edge_material(child)

    # Prune any floating components not connected to fixed anchors.
    Genome.prune_components_without_fixed_anchor_inplace(child.bridge)
    child.joints = child.bridge.get("joints", [])
    child.edges = child.bridge.get("edges", [])
    repair_mutable_joints_out_of_terrain(child.bridge, level)
    child.joints = child.bridge.get("joints", [])

    return child
