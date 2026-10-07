from setup import configure_acceptance
configure_acceptance()
import runpy
from setup import ACC
runpy.run_path(str(ACC/'attach_browser_media.py'),run_name='__main__')
