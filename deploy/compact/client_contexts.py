"""Add newly introduced Feign contexts without regenerating a local deployment."""
from pathlib import Path
import copy
import datetime
import json
import re
import uuid
import yaml


def contexts(sources):
    result = {}
    for source in sources:
        for declaration in re.findall(r'@FeignClient\(([^)]*)\)', source.read_text(encoding='utf8')):
            service = re.search(r'(?:value|name)\s*=\s*"([^"]+)"', declaration) or re.match(r'\s*"([^"]+)"', declaration)
            context = re.search(r'contextId\s*=\s*"([^"]+)"', declaration)
            if service and context:
                name, owner = context.group(1), service.group(1)
                if name in result and result[name] != owner:
                    raise ValueError('Conflicting Feign context ownership')
                result[name] = owner
    return result


def synchronize(local, aliases, declared):
    local = Path(local).resolve()
    directory = local / 'configs'
    if directory.is_symlink() or directory.resolve().parent != local:
        raise ValueError('Configuration directory must stay in this deployment')
    pending = []
    for alias in aliases:
        if not re.fullmatch(r'[a-z][a-z0-9-]*', alias):
            raise ValueError('Invalid module alias')
        path = directory / (alias + '.yml')
        if path.is_symlink() or path.resolve().parent != directory:
            raise ValueError('Module configuration escapes this deployment')
        before = path.read_bytes()
        document = yaml.safe_load(before)
        clients = document['spring']['cloud']['openfeign']['client']['config']
        original = copy.deepcopy(document)
        added = []
        for context, service in sorted(declared.items()):
            if context in clients:
                continue
            template = clients.get(service) or clients.get(service.removesuffix('-service'))
            if not isinstance(template, dict) or not template.get('url'):
                raise ValueError('A trusted existing service URL is required for ' + context)
            clients[context] = copy.deepcopy(template)
            added.append(context)
        if added:
            verification = copy.deepcopy(document)
            for context in added:
                del verification['spring']['cloud']['openfeign']['client']['config'][context]
            if verification != original:
                raise ValueError('An existing deployment setting changed')
            pending.append((path, before, yaml.safe_dump(document, sort_keys=False).encode('utf8'), added))
    # Validate the entire update before writing any configuration. Backups stay private.
    backup = None
    if pending:
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
        backup = local / 'client-context-sync' / stamp
        backup.mkdir(parents=True)
    for path, before, after, added in pending:
        (backup / path.name).write_bytes(before)
        temporary = path.with_suffix('.sync.tmp')
        temporary.write_bytes(after)
        temporary.replace(path)
    result = dict(status='PASSED', updatedModules=[path.stem for path, *_ in pending],
                  addedContexts=sorted({context for *_, added in pending for context in added}),
                  existingSettingsPreserved=True, composeChanged=False)
    if backup:
        (backup / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf8')
    return result


if __name__ == '__main__':
    from setup import LOCAL, ROOT, MODULES
    print(json.dumps(synchronize(LOCAL, MODULES, contexts(ROOT.glob('tj-*/**/src/main/java/**/*Client.java')))))
