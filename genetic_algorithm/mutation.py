from genome import Genome
import random
import math

MAX_EDGE_LEN = 12.5

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

            a, b = min(i, j), max(i, j)

            for edge in genome.edges:
                a_test = min(edge["from"], edge["to"])
                b_test = max(edge["from"], edge["to"])
                if (a, b) == (a_test, b_test):
                    break
            else:
                genome.edges.append({
                    "from": i,
                    "to": j,
                    "material": "ASPHALT"
                })
                return

    # create new joint

    angle = random.uniform(0, 2 * math.pi)
    dist = random.uniform(1.0, MAX_EDGE_LEN)

    new_x = genome.joints[i]["x"] + (dist * math.cos(angle))
    new_y = genome.joints[i]["y"] + (dist * math.sin(angle))

    new_index = len(genome.joints)

    genome.joints.append({
        "x": new_x,
        "y": new_y,
        "fixed": False
    })

    genome.edges.append({
        "from": i,
        "to": new_index,
        "material": "ASPHALT"
    })


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
