"""Pure ownership checks shared by retirement commands; no Docker mutation here."""
from pathlib import Path


def expected_services(compose):
    services = set(compose.get('services', {}))
    if not services or not {'mysql', 'app-gateway', 'web-student', 'web-admin'} <= services:
        raise RuntimeError('A complete compact runtime topology is required')
    return services


def healthy_runtime(containers, compose, project):
    expected = expected_services(compose)
    actual = {}
    for item in containers:
        labels = item['Config'].get('Labels') or {}
        if labels.get('com.docker.compose.project') != project:
            raise RuntimeError('Unexpected project in runtime inventory')
        service = labels.get('com.docker.compose.service')
        if service in actual:
            raise RuntimeError('Duplicate runtime service')
        if not item['State'].get('Running') or item['State'].get('Health', {}).get('Status') != 'healthy':
            raise RuntimeError('Every final service must be running and healthy')
        actual[service] = item['Id']
    if set(actual) != expected:
        raise RuntimeError('Runtime service inventory differs from the effective Compose configuration')
    return actual


def owned_container(item, projects, root):
    labels = item['Config'].get('Labels') or {}
    files = labels.get('com.docker.compose.project.config_files', '').split(',')
    return (labels.get('com.docker.compose.project') in projects and bool(files)
            and all(name and Path(name).resolve().is_relative_to(Path(root).resolve()) for name in files))


def disposable_volume(item, projects, referenced, proof):
    """Only explicitly recreated synthetic projects may discard their isolated data."""
    labels = item.get('Labels') or {}
    project = labels.get('com.docker.compose.project')
    return (project in projects and item['Name'] not in referenced
            and proof.get('project') == project and proof.get('status') == 'PASSED'
            and proof.get('syntheticOnly') is True and proof.get('recreationVerified') is True
            and bool(proof.get('sourceCommit')))
