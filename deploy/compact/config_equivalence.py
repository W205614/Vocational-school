"""Compare final/demo and benchmark configuration, allowing only ports and local credentials."""
import hashlib
import json
from pathlib import Path
import re
import yaml
from evidence import digest,tree_digest

PRIVATE={('spring','datasource','password'),('spring','rabbitmq','password'),('encrypt','key-store','password'),('encrypt','key-store','secret')}
def config_fingerprint(home):
    home=Path(home)
    return hashlib.sha256((tree_digest(home/'.local/configs')+digest(home/'compose.yaml')).encode()).hexdigest()

def normalized(home):
    home=Path(home);compose=yaml.safe_load((home/'compose.yaml').read_text(encoding='utf8'))
    offset=int(compose['services']['app-gateway']['environment']['APP_PORT'])-24310
    ports={number+offset:number for number in (24001,24002,24003,24004,24310,25001,25002,25003,25004,25005,25006,25007,24316,24379,24373,24920,24500,24501)}
    def value(item,path=()):
        if path in PRIVATE:return '<local-credential>'
        if isinstance(item,dict):return {key:value(child,path+(key,)) for key,child in item.items()}
        if isinstance(item,list):return [value(child,path) for child in item]
        if isinstance(item,int) and path[-1:] in [('port',),('internal-port',)]:return ports.get(item,item)
        if isinstance(item,str):
            if path[-1:] in [('internal-ports',),('net.ipv4.ip_local_reserved_ports',)]:return ','.join(str(ports.get(int(port),int(port))) for port in item.split(','))
            return re.sub(r'(?<=:)(\d{4,5})(?=[/:]|$)',lambda match:str(ports.get(int(match.group()),int(match.group()))),item)
        return item
    configs={path.name:value(yaml.safe_load(path.read_text(encoding='utf8'))) for path in sorted((home/'.local/configs').glob('*.yml'))}
    extra={path.name:digest(path) for path in sorted((home/'.local/configs').iterdir()) if path.is_file() and path.suffix!='.yml'}
    # Compose identities and data volume contents are environment-specific. Resource
    # limits, commands, mounts, image IDs and all service behavior remain comparable.
    services={name:value({key:child for key,child in service.items() if key not in ('container_name','hostname')}) for name,service in compose['services'].items()}
    for service in services.values():
        if 'environment' in service and 'APP_PORT' in service['environment']:
            service['environment']['APP_PORT']=str(ports.get(int(service['environment']['APP_PORT']),int(service['environment']['APP_PORT'])))
    return {'configs':configs,'extraConfigFiles':extra,'services':services,'volumes':compose.get('volumes',{})}

def equivalent(left,right):return normalized(left)==normalized(right)
