from collections import deque
from genetic_algorithm.genome import Genome
import random
import math
import uuid

MAX_EDGE_LEN = 12.5
# At this fail streak (and above), joint picks are fully weighted toward large hop distance from fixed.
TOPO_BIAS_FULL_AT_STREAK = 25
# Weight ~ (hops + 1) ** TOPO_WEIGHT_POWER when topology bias is active.
TOPO_WEIGHT_POWER = 2.0


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
) -> int:
    """Pick one index from candidates; as fail_streak grows, prefer larger hop_dist[j]."""
    if not candidates:
        raise ValueError("candidates must be non-empty")
    if len(candidates) == 1:
        return candidates[0]

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


# shift mutable joint
def _move_joint(genome: Genome, sigma: float = 1.0, fail_streak: int = 0):
    indices = genome.mutable_joints()
    if not indices:
        return

    hop = _shortest_hops_to_nearest_fixed(genome)
    i = _pick_joint_index_from_candidates(genome, indices, fail_streak, hop)
    joint = genome.joints[i]

    joint['x'] += random.uniform(-sigma, sigma)
    joint['y'] += random.uniform(-sigma, sigma)


# add new edge from existing joint
def _add_edge(genome: Genome, fail_streak: int = 0):
    num_joints = len(genome.joints)

    if num_joints == 0:
        return

    hop = _shortest_hops_to_nearest_fixed(genome)
    all_indices = list(range(num_joints))
    i = _pick_joint_index_from_candidates(genome, all_indices, fail_streak, hop)

    if random.random() < min(0.9, 0.5 + (fail_streak * 0.03))  and num_joints > 1:
        # connect existing joints
        material = "ASPHALT"
        valid_j: list[int] = []
        for j in range(num_joints):
            if j == i:
                continue
            if genome.edge_length(i, j) > MAX_EDGE_LEN:
                continue
            if _edge_already_exists(genome, i, j, material):
                continue
            valid_j.append(j)

        if valid_j:
            j = _pick_joint_index_from_candidates(genome, valid_j, fail_streak, hop)
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

    # create new joint

    angle = random.uniform(0, 2 * math.pi)
    dist = random.uniform(1.0, MAX_EDGE_LEN)

    new_x = genome.joints[i]["x"] + (dist * math.cos(angle))
    new_y = genome.joints[i]["y"] + (dist * math.sin(angle))

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
        "material": "ASPHALT",
        "uuid": str(uuid.uuid4()),
        "from_uuid": str(genome.joints[i]["uuid"]),
        "to_uuid": str(new_joint["uuid"]),
    }
    genome.edges.append(new_edge)
    genome.edges[-1] = Genome._normalize_edge_dict(new_edge)


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

    hop = _shortest_hops_to_nearest_fixed(genome)
    remove_index = _pick_joint_index_from_candidates(genome, mutable, fail_streak, hop)
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
def mutate(parent: Genome, fail_streak: int = 0):
    child = parent.clone()


    # Small continuous tweak
    if random.random() < 0.70:
        _move_joint(child, fail_streak=fail_streak)

    # r = random.random()
    # if r < 0.1:
    #     _remove_joint(child)
    # elif r < 0.2:
    #     _add_edge(child)
    # elif r < 0.3:
    #     _remove_edge(child)

    # Joint-structure mutation
    r_joint = random.random()

    if r_joint < max(0.0, 0.1 - (fail_streak * 0.01)):
        _remove_joint(child, fail_streak)

    # Edge-structure mutation
    r_edge = random.random()
    if r_edge < min(1.0, 0.6 + (fail_streak * 0.01)):
        _add_edge(child, fail_streak)

    if r_edge < max(0.0, 0.55 - (fail_streak * 0.01)):
        _remove_edge(child)

    # Prune any floating components not connected to fixed anchors.
    Genome.prune_components_without_fixed_anchor_inplace(child.bridge)
    child.joints = child.bridge.get("joints", [])
    child.edges = child.bridge.get("edges", [])

    return child
