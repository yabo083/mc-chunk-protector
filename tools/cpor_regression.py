from pathlib import Path

from rcon_client import execute


CONFIG = Path(__file__).parents[1] / "dev-server" / "kubejs" / "config" / "regions.json"
COMMANDS = [
    "cpor status 123456 -123456",
    "cpor add freeze rect 123456 -123456 123457 -123455",
    "cpor status 123456 -123456",
    "cpor remove freeze rect 123456 -123456 123457 -123455",
    "cpor status 123456 -123456",
    "cpor add place rect 123456 -123456 123456 -123456",
    "cpor status 123456 -123456",
    "cpor remove place rect 123456 -123456 123456 -123456",
]


def main():
    original = CONFIG.read_bytes()
    try:
        results = execute(COMMANDS)
        for command, response in results:
            print(f"{command} => {response}")

        assert "freeze=false" in results[0][1]
        assert "freeze=true" in results[2][1]
        assert "freeze=false" in results[4][1]
        assert "place=true" in results[6][1]
    finally:
        CONFIG.write_bytes(original)
        execute(["cpor reload"])

    print("PASS: /cpor add, remove, status, and config restore")


if __name__ == "__main__":
    main()
