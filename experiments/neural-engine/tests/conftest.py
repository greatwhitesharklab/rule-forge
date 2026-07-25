"""Pytest bootstrap: make memory/ model/ training/ importable.

experiments/neural-engine is not a valid Python package name (hyphen), so
tests run with this directory on sys.path instead of a package install.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
