from rcon_client import execute


FROZEN_X = 40  # chunk (2, 0), configured as freeze-updates in the dev server
FROZEN_EDGE_X = 32  # west edge of chunk (2, 0)
OUTSIDE_EDGE_X = 31  # east edge of adjacent, unfrozen chunk (1, 0)
CONTROL_X = 8  # chunk (0, 0), not frozen
Y = 72


def state_check(x, z, first, second):
    return (
        f"execute if block {x} {Y} {z} {first} "
        f"if block {x + 1} {Y} {z} {second} run time query gametime"
    )


def pair_check(first_x, second_x, z, first, second):
    return (
        f"execute if block {first_x} {Y} {z} {first} "
        f"if block {second_x} {Y} {z} {second} run time query gametime"
    )


commands = [
    f"fill {FROZEN_X - 1} {Y - 1} 0 {FROZEN_X + 2} {Y} 3 air",
    f"fill {FROZEN_X - 1} {Y - 1} 0 {FROZEN_X + 2} {Y - 1} 3 stone",
    f"fill {CONTROL_X - 1} {Y - 1} 0 {CONTROL_X + 2} {Y} 3 air",
    f"fill {CONTROL_X - 1} {Y - 1} 0 {CONTROL_X + 2} {Y - 1} 3 stone",
    f"fill {OUTSIDE_EDGE_X} {Y - 1} 4 {FROZEN_EDGE_X} {Y} 6 air",
    f"fill {OUTSIDE_EDGE_X} {Y - 1} 4 {FROZEN_EDGE_X} {Y - 1} 6 stone",
    f"setblock {FROZEN_X} {Y} 0 oak_fence",
    f"setblock {FROZEN_X + 1} {Y} 0 oak_fence",
    f"setblock {CONTROL_X} {Y} 0 oak_fence",
    f"setblock {CONTROL_X + 1} {Y} 0 oak_fence",
    f"setblock {FROZEN_X} {Y} 2 redstone_wire",
    f"setblock {FROZEN_X + 1} {Y} 2 redstone_wire",
    f"setblock {CONTROL_X} {Y} 2 redstone_wire",
    f"setblock {CONTROL_X + 1} {Y} 2 redstone_wire",
    f"setblock {FROZEN_EDGE_X} {Y} 4 oak_fence",
    f"setblock {OUTSIDE_EDGE_X} {Y} 4 oak_fence",
    f"setblock {FROZEN_EDGE_X} {Y} 6 redstone_wire",
    f"setblock {OUTSIDE_EDGE_X} {Y} 6 redstone_wire",
]

checks = {
    "frozen fence keeps initial states": state_check(
        FROZEN_X, 0, "oak_fence[east=false]", "oak_fence[west=true]"
    ),
    "control fence connects": state_check(
        CONTROL_X, 0, "oak_fence[east=true]", "oak_fence[west=true]"
    ),
    "frozen redstone keeps initial states": state_check(
        FROZEN_X,
        2,
        "redstone_wire[power=0,east=none,west=none,north=none,south=none]",
        "redstone_wire[power=0,east=side,west=side,north=none,south=none]",
    ),
    "control redstone updates": state_check(
        CONTROL_X,
        2,
        "redstone_wire[power=0,east=side,west=side,north=none,south=none]",
        "redstone_wire[power=0,east=side,west=side,north=none,south=none]",
    ),
    "boundary frozen fence ignores outside update": pair_check(
        FROZEN_EDGE_X,
        OUTSIDE_EDGE_X,
        4,
        "oak_fence[west=false]",
        "oak_fence[east=true]",
    ),
    "boundary frozen redstone ignores outside update": pair_check(
        FROZEN_EDGE_X,
        OUTSIDE_EDGE_X,
        6,
        "redstone_wire[power=0,east=none,west=none,north=none,south=none]",
        "redstone_wire[power=0,east=side,west=side,north=none,south=none]",
    ),
}

execute(commands)
failed = []
for name, command in checks.items():
    response = execute([command])[0][1]
    passed = bool(response.strip())
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
    if not passed:
        failed.append(name)

if failed:
    raise SystemExit(1)
