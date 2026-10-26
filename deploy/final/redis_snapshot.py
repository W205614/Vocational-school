"""Compatibility import; binary snapshot implementation lives in deploy/operations."""
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'operations'))
from runtime_redis_snapshot import SEMANTIC_SCRIPT,SCRIPT,transfer
