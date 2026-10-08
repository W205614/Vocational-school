"""Restore recorded behavior into independently owned ports, mounts and volumes."""
import copy
from pathlib import Path
import re
import yaml


def translated(value,old_offset,new_offset,path=()):
    bases=(24001,24002,24003,24004,24310,24316,24379,24373,24920,24500,24501,25001,25002,25003,25004)
    ports={port+old_offset:port+new_offset for port in bases}
    if isinstance(value,dict):return {key:translated(child,old_offset,new_offset,path+(key,)) for key,child in value.items()}
    if isinstance(value,list):return [translated(child,old_offset,new_offset,path) for child in value]
    if isinstance(value,int) and path[-1:] in [('port',),('internal-port',)]:return ports.get(value,value)
    if isinstance(value,str):
        if path[-1:] in [('password',),('secret',)]:return value
        if path[-1:] == ('APP_PORT',):return str(ports.get(int(value),int(value)))
        if path[-1:] == ('internal-ports',):return ','.join(str(ports.get(int(port),int(port))) for port in value.split(','))
        return re.sub(r'(?<=:)(\d{4,5})(?=[/:]|$)',lambda match:str(ports.get(int(match.group()),int(match.group()))),value)
    return value


def recorded_configuration(compose,configs,home,project,offset,images,broker_hostname):
    old_offset=int(compose['services']['app-gateway']['environment']['APP_PORT'])-24310
    if set(compose['services'])!=set(images):raise RuntimeError('Every recorded service needs immutable image provenance')
    if any(settings for settings in compose.get('volumes',{}).values()):raise RuntimeError('Recovery cannot reuse explicitly named or external volumes')
    restored=translated(copy.deepcopy(compose),old_offset,offset)
    restored['name']=project
    for name,service in restored['services'].items():
        service.pop('container_name',None);service['image']=images[name]
        for mount in service.get('volumes',[]):
            source=mount.split(':')[0] if isinstance(mount,str) else mount['source']
            named=source in restored['volumes'] and (isinstance(mount,str) or mount.get('type')=='volume')
            local=source.startswith('./.local/') and (Path(home)/source).resolve().is_relative_to((Path(home)/'.local').resolve())
            if not named and not local:raise RuntimeError('Recovery mount points outside its fresh home')
    restored['services']['rabbitmq']['hostname']=broker_hostname
    restored['services']['rabbitmq']['volumes']=[{'type':'volume','source':'compact_rabbit','target':'/var/lib/rabbitmq','volume':{'nocopy':True}}]
    destination=Path(home)/'.local/configs';destination.mkdir(parents=True,exist_ok=True)
    for path in Path(configs).iterdir():
        if not path.is_file() or path.suffix not in ('.yml','.security'):raise RuntimeError('Unexpected recorded module configuration')
        if path.suffix=='.yml':
            content=translated(yaml.safe_load(path.read_text(encoding='utf8')),old_offset,offset)
            (destination/path.name).write_text(yaml.safe_dump(content,allow_unicode=True,sort_keys=False),encoding='utf8')
        else:(destination/path.name).write_bytes(path.read_bytes())
    return restored
