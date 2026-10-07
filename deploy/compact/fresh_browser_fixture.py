import runpy
from setup import configure_acceptance,ACC
configure_acceptance();runpy.run_path(str(ACC/'browser_fixture.py'),run_name='__main__')
