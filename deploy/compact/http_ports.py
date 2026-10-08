"""Keep application listeners out of automatic outbound socket allocation."""

def listener_sysctls(public_port, internal_port=None):
    ports = {int(public_port)}
    if internal_port is not None:
        ports.update(range(int(internal_port), int(internal_port) + 4))
    if any(port < 1 or port > 65535 for port in ports):
        raise ValueError('HTTP listener port outside valid TCP range')
    return {'net.ipv4.ip_local_reserved_ports': ','.join(str(port) for port in sorted(ports))}
