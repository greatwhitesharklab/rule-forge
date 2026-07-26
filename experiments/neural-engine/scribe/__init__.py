"""Scribe: local-LLM experience induction -> slot writes (design doc §1.1/§4).

P2 component. The encoder renders cases; the Scribe induces RULES from case
groups. Public surface:

  Scribe / ScribeCase / ExperienceDraft  -- induction pipeline (induce.py)
  check_statement                        -- quality gate (writer.py)
  ScribeWriter / write_experiences       -- slot write adapter + mode switch
"""

from .draft import ExperienceDraft, ScribeCase
from .induce import InduceReport, Scribe
from .writer import (
    ScribeWriter,
    WriteReport,
    check_statement,
    write_experiences,
)

__all__ = [
    "ExperienceDraft",
    "InduceReport",
    "Scribe",
    "ScribeCase",
    "ScribeWriter",
    "WriteReport",
    "check_statement",
    "write_experiences",
]
