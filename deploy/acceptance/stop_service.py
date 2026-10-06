"""Identity-checked stop using Windows CIM, without external Python packages."""
import argparse,subprocess,shutil
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('service');args=parser.parse_args()
raise SystemExit(subprocess.run([shutil.which('pwsh') or 'powershell','-NoProfile','-File',str(Path(__file__).with_suffix('.ps1')),'-Service',args.service]).returncode)
