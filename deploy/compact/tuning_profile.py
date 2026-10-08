"""Bound diagnostic candidates to the agreed database and executor budget."""
import os


def profile(database_count,values=None):
    values=os.environ if values is None else values
    pool=int(values.get('TJ_PERF_POOL_SIZE','4'))
    workers=int(values.get('TJ_PERF_OPERATION_THREADS','1'))
    interval=int(values.get('TJ_PERF_INTERVAL_MS','750'))
    if pool not in (4,8) or workers not in (1,2) or interval not in (250,750):
        raise ValueError('Only agreed pool, worker and interval candidates are accepted')
    if database_count*pool+16>160:
        raise ValueError('Database pool total exceeds the 160-connection budget with control capacity reserved')
    return {'pool':pool,'workers':workers,'interval':interval,'databaseConnectionCeiling':database_count*pool+16}
