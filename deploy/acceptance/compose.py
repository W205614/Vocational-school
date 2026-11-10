"""Invoke acceptance Compose with decrypted credentials in the child environment."""
import subprocess
import sys
from prepare import COMPOSE, local_secrets

if __name__ == '__main__':
    local_secrets()
    raise SystemExit(subprocess.run(COMPOSE + sys.argv[1:]).returncode)
