"""Read the selected web transport without changing the deployment."""
from pathlib import Path
import json


def web_origin(local, port):
    marker = Path(local) / 'web-tls.json'
    if marker.exists():
        value = json.loads(marker.read_text(encoding='utf8'))
        if value != {'scheme': 'https', 'host': 'localhost'}:
            raise ValueError('Unrecognized web TLS configuration')
        return 'https://localhost:' + str(port)
    return 'http://127.0.0.1:' + str(port)
