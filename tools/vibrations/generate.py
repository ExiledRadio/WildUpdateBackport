"""Assets for game events and vibrations: the vibration particle's sprite, from 1.19.2.

    python -I tools/vibrations/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

assets.texture("particle/vibration")
print("vibrations: particle sprite")
