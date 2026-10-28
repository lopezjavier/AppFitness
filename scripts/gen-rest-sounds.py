"""Generates the synthesised end-of-rest sound candidates (own work, no third-party audio, no licence).

Writes two ~1 s mono 16-bit WAV files into composeApp/src/androidMain/res/raw/:
  rest_chime.wav        a soft two-partial bell with an exponential decay
  rest_double_beep.wav  two short 1 kHz beeps

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


def beep(duration: float, frequency: float) -> list[float]:
    count = int(RATE * duration)
    fade = int(RATE * 0.01)
    out = []
    for i in range(count):
        edge = min(1.0, i / fade, (count - 1 - i) / fade)
        out.append(math.sin(2 * math.pi * frequency * i / RATE) * edge)
    return out


def double_beep() -> list[float]:
    gap = [0.0] * int(RATE * 0.15)
    tail = [0.0] * (RATE - 2 * int(RATE * 0.25) - len(gap))
    return beep(0.25, 1000) + gap + beep(0.25, 1000) + tail


if __name__ == "__main__":
    write("rest_chime.wav", chime())
    write("rest_double_beep.wav", double_beep())
