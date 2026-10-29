"""Generates the synthesised end-of-rest sound (own work, no third-party audio, no licence).

Writes a ~1 s mono 16-bit WAV file into composeApp/src/androidMain/res/raw/:
  rest_chime.wav  a soft two-partial bell with an exponential decay

Usage: python scripts/gen-rest-sounds.py   (deterministic output; commit the generated files)
"""

import math
import struct
import wave
from pathlib import Path

RATE = 22050
PEAK = 0.6
OUT = Path(__file__).resolve().parent.parent / "composeApp/src/androidMain/res/raw"


def write(name: str, samples: list[float]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / name), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * PEAK * 32767)) for s in samples))


def chime() -> list[float]:
    count = RATE
    out = []
    for i in range(count):
        t = i / RATE
        envelope = math.exp(-4.5 * t) * min(1.0, t / 0.005)
        tone = math.sin(2 * math.pi * 880 * t) + 0.4 * math.sin(2 * math.pi * 1760 * t)
        out.append(tone / 1.4 * envelope)
    return out


if __name__ == "__main__":
    write("rest_chime.wav", chime())
