from genetic_algorithm.genome import Genome
import random
import math
import uuid

MAX_EDGE_LEN = 12.5

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
def _move_joint(genome: Genome, sigma: float = 1.0):
    indices = genome.mutable_joints()
    if not indices:
        return
    
    i = random.choice(indices)
    joint = genome.joints[i]

    joint['x'] += random.uniform(-sigma, sigma)
    joint['y'] += random.uniform(-sigma, sigma)

# add new edge
def _add_edge_old(genome: Genome):
    num_joints = len(genome.joints)
    if num_joints < 2:
        return
    
    for _ in range(10):
        i, j = random.sample(range(num_joints), 2)

        if genome.edge_length(i, j) > MAX_EDGE_LEN:
            continue

        a, b = sorted((i, j))
        duplicate = False

        for edge in genome.edges:
            a_test, b_test = sorted((edge['from'], edge['to']))
            
            if (a, b) == (a_test, b_test):
                duplicate = True
                break
        
        else:
            genome.edges.append({
                'from': i,
                'to': j,
                'material': 'ASPHALT'
            })

        return

# add new edge from existing joint
def _add_edge(genome: Genome):
    num_joints = len(genome.joints)

    if num_joints == 0:
        return
    
    i = random.randrange(num_joints)

    if random.random() < 0.5 and num_joints > 1:
        # connect existing joints

        for _ in range(10):
            j = random.randrange(num_joints)
            if i == j:
                continue

            if genome.edge_length(i, j) > MAX_EDGE_LEN:
                continue

            material = "ASPHALT"
            if _edge_already_exists(genome, i, j, material):
                continue

            new_edge = {"from": i, "to": j, "material": material, "uuid": str(uuid.uuid4())}
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

# clone genome, mutate it, return new genome
def mutate(parent: Genome):
    child = parent.clone()
    
    r = random.random()

    if r < 0.75:
        _move_joint(child)
    elif r < 0.9:
        _add_edge(child)
    else:
        _remove_edge(child)

    return child
