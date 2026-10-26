"""Application-owned configuration and mounts, shared by fresh and incremental delivery."""
import copy

GROUPS = {'identity': ('auth', 'user'), 'commerce': ('trade', 'pay', 'promotion'),
          'education': ('course', 'learning', 'exam', 'remark'),
          'support': ('media', 'search', 'message', 'data'), 'gateway': ()}


def application_mounts(group):
    if group not in GROUPS:
        raise ValueError('Unknown compact application group')
    mounts = [f'./.local/configs/{name}.yml:/run/compact/configs/{name}.yml:ro'
              for name in (group, *GROUPS[group])]
    if group == 'identity':
        mounts.append('./.local/signing.jks:/run/compact/signing.jks:ro')
    if group == 'support':
        mounts.append('./.local/objects:/run/objects')
    return mounts


def application_dependencies(group):
    if group == 'gateway':
        names = ('redis', 'app-identity')
    elif group == 'support':
        names = ('mysql', 'redis', 'rabbitmq', 'elasticsearch')
    elif group in GROUPS:
        names = ('mysql', 'redis', 'rabbitmq')
    else:
        raise ValueError('Unknown compact application group')
    return {name: {'condition': 'service_healthy'} for name in names}


def isolate_applications(compose):
    """Narrow only the legacy shared mounts; keep image, state, and custom binds intact."""
    result = copy.deepcopy(compose)
    legacy = {'./.local/configs:/run/compact/configs:ro',
              './.local/signing.jks:/run/compact/signing.jks:ro',
              './.local/objects:/run/objects'}
    for group in GROUPS:
        service = result['services'].get('app-' + group)
        if service is None:
            continue
        current = service.get('volumes', [])
        # Long-form/custom mounts need a dedicated migration: never broaden or discard them.
        if any(isinstance(value, dict) and value.get('target') in
               {'/run/compact/configs', '/run/compact/signing.jks', '/run/objects'} for value in current):
            raise RuntimeError('Custom application mount requires explicit migration')
        shared_config = './.local/configs:/run/compact/configs:ro' in current
        if shared_config:
            service['volumes'] = [value for value in current if not isinstance(value, str) or value not in legacy]
            for value in application_mounts(group):
                if value not in service['volumes']:
                    service['volumes'].append(value)
        if 'depends_on' in service:
            # Preserve additional application dependencies chosen by an operator.
            service['depends_on'] = {key: value for key, value in service['depends_on'].items()
                                     if key not in {'mysql', 'redis', 'rabbitmq', 'elasticsearch'}}
            service['depends_on'].update(application_dependencies(group))
    return result
