"""Validate Nacos runtime isolation in actual Spring Boot artifacts."""
import io
import argparse
from pathlib import Path
import zipfile


def verify(path, compact=False):
    with zipfile.ZipFile(path) as application:
        libraries = [name for name in application.namelist() if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")]
        if compact:
            if any(Path(name).name.startswith(("nacos-", "grpc-", "protobuf-", "proto-google-", "perfmark-")) for name in libraries):
                raise ValueError("Disabled Nacos runtime remains in compact artifact: " + str(path))
            return
        clients = [name for name in libraries if Path(name).name.startswith("nacos-client-") and not Path(name).name.startswith("nacos-client-basic-")]
        if len(clients) != 1 or not clients[0].endswith("-pure.jar"):
            raise ValueError("Standalone Nacos requires exactly one pure SDK")
        for artifact in ("nacos-api", "nacos-common", "guava"):
            if not any(Path(name).name.startswith(artifact + "-") for name in libraries):
                raise ValueError("Missing pure SDK runtime dependency: " + artifact)
        for name in libraries:
            if not Path(name).name.startswith("nacos-"):
                continue
            with zipfile.ZipFile(io.BytesIO(application.read(name))) as library:
                if any(entry.startswith("com/alibaba/nacos/shaded/") and entry.endswith(".class") for entry in library.namelist()):
                    raise ValueError("Shaded dependencies remain in " + Path(name).name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compact", action="store_true")
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()
    for path in args.paths:
        verify(path, args.compact)
    print("Nacos runtime packaging verified")
