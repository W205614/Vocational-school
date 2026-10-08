"""Bind freshly packaged artifacts to a clean production tree before tagging images."""
import hashlib
import json
from pathlib import Path
import subprocess

def production(path):
    return '/src/main/' in path or path.endswith('pom.xml') or path.startswith('frontend/') and '/e2e/' not in path

def clean_commit(root):
    git=['git','-C',str(root)]
    commit=subprocess.check_output(git+['rev-parse','HEAD'],text=True).strip()
    changed=subprocess.check_output(git+['diff','--name-only',commit],text=True).splitlines()
    untracked=subprocess.check_output(git+['ls-files','--others','--exclude-standard'],text=True).splitlines()
    if any(production(path) for path in changed+untracked):raise RuntimeError('Commit production source before creating acceptance images')
    return commit

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def packaged(root,local,commit,mode,jars):
    if clean_commit(root)!=commit:raise RuntimeError('Production source changed while packaging')
    value={'sourceCommit':commit,'mode':mode,'jars':{name:digest(path) for name,path in jars.items()}}
    (Path(local)/'package-source.json').write_text(json.dumps(value,indent=2),encoding='utf8')
    return value

def require_package(local,commit,mode):
    value=json.loads((Path(local)/'package-source.json').read_text(encoding='utf8'))
    if value.get('sourceCommit')!=commit or value.get('mode')!=mode:raise RuntimeError('Fresh package/source journal required for this build and profile')
    return value
