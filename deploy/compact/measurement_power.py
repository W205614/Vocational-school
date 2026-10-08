"""Keep Windows awake during formal sampling, restoring the request on every exit."""
from functools import wraps
import sys


def awake_during_measurement(function):
    @wraps(function)
    def measured(*args,**kwargs):
        kernel=None
        if sys.platform=='win32':
            import ctypes
            kernel=ctypes.windll.kernel32
            if not kernel.SetThreadExecutionState(0x80000001):
                raise RuntimeError('Unable to request continuous system availability for measurement')
        try:return function(*args,**kwargs)
        finally:
            if kernel is not None:kernel.SetThreadExecutionState(0x80000000)
    return measured
