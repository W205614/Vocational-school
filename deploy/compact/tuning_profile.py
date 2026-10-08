"""Bound diagnostic candidates to the agreed database and executor budget."""
import os
OBSERVER_CONNECTIONS = 16
CONTROL_CONNECTIONS = 16
MYSQL_CONNECTION_LIMIT = 160


def profile(database_count,values=None):
    values=os.environ if values is None else values
    pool=int(values.get('TJ_PERF_POOL_SIZE','8'))
    workers=int(values.get('TJ_PERF_OPERATION_THREADS','2'))
    interval=int(values.get('TJ_PERF_INTERVAL_MS','250'))
    if pool not in (4,8) or workers not in (1,2) or interval not in (250,750):
        raise ValueError('Only agreed pool, worker and interval candidates are accepted')
    ceiling=database_count*pool+OBSERVER_CONNECTIONS+CONTROL_CONNECTIONS
    if ceiling>MYSQL_CONNECTION_LIMIT:
        raise ValueError('Database pool total exceeds the 160-connection budget with control capacity reserved')
    return {'pool':pool,'workers':workers,'interval':interval,'databaseConnectionCeiling':ceiling,
            'observerConnections':OBSERVER_CONNECTIONS,'controlConnections':CONTROL_CONNECTIONS}
