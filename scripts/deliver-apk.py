#!/usr/bin/env python3
"""Build the prod debug APK and install it on connected physical Android devices."""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APK_DIR = os.path.join("androidApp", "build", "outputs", "apk", "prod", "debug")
IS_WINDOWS = os.name == "nt"


def find_adb():
    found = shutil.which("adb")
    if found:
        return found
    names = ["adb.exe"] if IS_WINDOWS else ["adb"]
    dirs = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    local = os.environ.get("LOCALAPPDATA")
    if IS_WINDOWS and local:
        dirs.append(os.path.join(local, "Android", "Sdk"))
    for d in dirs:
        if not d:
            continue
        for n in names:
            p = os.path.join(d, "platform-tools", n)
            if os.path.isfile(p):
                return p
    return None


def parse_devices(output):
    """Parse `adb devices` / `adb devices -l` output into (serial, state) pairs."""
    result = []
    for line in output.replace("\r", "").splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2:
            result.append((parts[0], parts[1]))
    return result


def select_serials(devices, include_emulator):
    """Return (serials to install on, warnings for unauthorized/offline devices)."""
    serials, warnings = [], []
    for serial, state in devices:
        if state in ("unauthorized", "offline"):
            warnings.append(
                f"Skipped {serial} ({state}): accept the USB debugging prompt on the device."
            )
            continue
        if state != "device":
            continue
        if serial.startswith("emulator-") and not include_emulator:
            continue
        serials.append(serial)
    return serials, warnings


def build():
    gradlew = os.path.join(ROOT, "gradlew.bat" if IS_WINDOWS else "gradlew")
    cmd = [gradlew, "-q", ":androidApp:assembleProdDebug"]
    fd, log = tempfile.mkstemp(suffix=".log")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            code = subprocess.call(cmd, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
        if code != 0:
            with open(log, encoding="utf-8", errors="replace") as f:
                tail = f.read().splitlines()[-60:]
            print("\n".join(tail), file=sys.stderr)
            print("Build FAILED", file=sys.stderr)
            return False
    finally:
        os.remove(log)
    print("Build OK")
    return True


def run(adb, *args):
    """Run adb; return (exit code, stdout, stderr) with CRs stripped."""
    p = subprocess.run([adb, *args], capture_output=True, text=True, errors="replace")
    return p.returncode, p.stdout.replace("\r", "").strip(), p.stderr.replace("\r", "").strip()


def main(argv=None):
    ap = argparse.ArgumentParser(prog="deliver-apk.py", description=__doc__)
    ap.add_argument("--no-build", action="store_true", help="Install the existing APK without building.")
    ap.add_argument("--serial", action="append", default=[], metavar="ID",
                    help="Install only on this device (repeatable).")
    ap.add_argument("--include-emulator", action="store_true", help="Also install on emulator-* devices.")
    args = ap.parse_args(argv)

    adb = find_adb()
    if not adb:
        print("error: adb not found. Add it to PATH or set ANDROID_HOME / ANDROID_SDK_ROOT "
              "(platform-tools).", file=sys.stderr)
        return 1

    if not args.no_build and not build():
        return 1

    apks = sorted(glob.glob(os.path.join(ROOT, APK_DIR, "*.apk")), key=os.path.getmtime, reverse=True)
    if not apks:
        print(f"error: no APK found under {APK_DIR}", file=sys.stderr)
        return 1
    apk = apks[0]  # newest
    if len(apks) > 1:
        print(f"warning: {len(apks)} APKs found; using the newest.", file=sys.stderr)
    print(f"APK: {apk}")

    serials = list(args.serial)
    if not serials:
        code, out, _ = run(adb, "devices")
        if code != 0:
            print("error: 'adb devices' failed", file=sys.stderr)
            return 1
        serials, warnings = select_serials(parse_devices(out), args.include_emulator)
        for w in warnings:
            print(w, file=sys.stderr)

    if not serials:
        print("No physical device connected.")
        print(f'Install later with: adb -s <serial> install -r "{apk}"')
        return 0

    fail = 0
    for s in serials:
        mcode, mout, _ = run(adb, "-s", s, "shell", "getprop", "ro.product.model")
        model = mout if mcode == 0 and mout else "unknown model"
        code, so, se = run(adb, "-s", s, "install", "-r", apk)
        out = "\n".join(x for x in (so, se) if x)
        if code == 0 and "Success" in out:
            print(f"OK     {s} ({model})")
        else:
            print(f"FAILED {s} ({model})", file=sys.stderr)
            print(out, file=sys.stderr)
            fail = 1
    return fail


if __name__ == "__main__":
    sys.exit(main())
