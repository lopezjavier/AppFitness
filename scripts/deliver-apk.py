#!/usr/bin/env python3
"""Build the prod debug APK and install it on connected physical Android devices."""
import argparse
import glob
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import time

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


REQUIRED_PROD_CONFIG = (
    ("supabase.prod.url", "SUPABASE_URL"),
    ("supabase.prod.publishableKey", "SUPABASE_PUBLISHABLE_KEY"),
    ("google.webClientId", "GOOGLE_WEB_CLIENT_ID"),
)


def read_local_properties(path):
    """Parse a minimal java .properties file; missing file -> {}.

    Only single-line `key=value` / `key:value` entries are read: no `key value` (whitespace separator) and no
    `\\` line continuations. A key written in a form that is not read is reported as missing, so the script
    fails safe instead of using a wrong value.
    """
    props = {}
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for raw in f:
                line = raw.strip()
                if not line or line[0] in "#!":
                    continue
                idx = [i for i in (line.find("="), line.find(":")) if i >= 0]
                if not idx:
                    continue
                i = min(idx)
                props[line[:i].strip()] = line[i + 1:].strip()
    except OSError:
        pass
    return props


def missing_prod_config(props, env):
    """Return the (property key, env var) pairs that resolve blank, like androidApp/build.gradle.kts."""
    missing = []
    for key, var in REQUIRED_PROD_CONFIG:
        if not (props.get(key) or "").strip() and not (env.get(var) or "").strip():
            missing.append((key, var))
    return missing


def check_prod_config():
    missing = missing_prod_config(read_local_properties(os.path.join(ROOT, "local.properties")), os.environ)
    if not missing:
        return True
    print("error: the prod build needs these values and they are missing:", file=sys.stderr)
    for key, var in missing:
        print(f"  - {key} (local.properties) or {var} (environment)", file=sys.stderr)
    print("Add them to local.properties (see planning/08-environment-setup.md) or export the variables.",
          file=sys.stderr)
    return False


EXERCISES_DIR = os.path.join("composeApp", "src", "commonMain", "composeResources", "files", "exercises")


def repdb_bundle_problem(root=ROOT):
    """Return an error message when the gitignored RepDB bundle is not imported, else None."""
    base = os.path.join(root, EXERCISES_DIR)
    has_library = os.path.isfile(os.path.join(base, "library.json"))
    has_image = bool(glob.glob(os.path.join(base, "images", "*.webp")))
    if has_library and has_image:
        return None
    return (f"the RepDB exercise bundle is missing under {base} (needs library.json and images/*.webp). "
            "Run: python scripts/import-repdb.py <path to Biblioteca_Ejercicios_Free> "
            "(see planning/08-environment-setup.md).")


def resolve_repdb_source(cli_value, props, env):
    """Source folder of the RepDB bundle: --repdb-source, then repdb.source (local.properties), then REPDB_SOURCE."""
    for value in (cli_value, props.get("repdb.source"), env.get("REPDB_SOURCE")):
        if value and value.strip():
            return value.strip()
    return None


def log(message, stream=None):
    print(message, file=stream or sys.stdout, flush=True)


def step_prod_config():
    """[1/5] prod config check."""
    log("[1/5] Checking prod config...")
    t = time.monotonic()
    ok = check_prod_config()
    if ok:
        log(f"[1/5] Prod config OK ({time.monotonic() - t:.1f}s)")
    return ok


def step_repdb(source, reimport):
    """[2/5] Import the RepDB bundle unless it is already imported."""
    log("[2/5] RepDB exercise bundle...")
    t = time.monotonic()
    if repdb_bundle_problem() is None and not reimport:
        log("[2/5] RepDB bundle already imported: skipped (use --reimport to force)")
        return True
    if not source or not os.path.isdir(source):
        shown = source or "(none)"
        print(f"error: RepDB source folder not found: {shown}. Pass --repdb-source PATH, set repdb.source in "
              "local.properties or the REPDB_SOURCE environment variable "
              "(the Biblioteca_Ejercicios_Free folder; see planning/08-environment-setup.md).", file=sys.stderr)
        return False
    log(f"[2/5] Importing RepDB bundle from {source}...")
    spec = importlib.util.spec_from_file_location("import_repdb", os.path.join(ROOT, "scripts", "import-repdb.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    try:
        count, images = mod.import_bundle(os.path.abspath(source))
    except mod.ImportError_ as e:
        print(f"error: RepDB import failed: {e}", file=sys.stderr)
        return False
    log(f"[2/5] RepDB imported: {count} exercises, {images} images copied ({time.monotonic() - t:.1f}s)")
    return True


def build(verbose=False):
    """[3/5] Gradle build."""
    gradlew = os.path.join(ROOT, "gradlew.bat" if IS_WINDOWS else "gradlew")
    cmd = [gradlew, "-q", ":androidApp:assembleProdDebug"]
    log("[3/5] Gradle build (assembleProdDebug)..." + (" streaming output" if verbose else " this can take minutes"))
    t = time.monotonic()
    if verbose:
        code = subprocess.call(cmd, cwd=ROOT)
        if code != 0:
            print(f"[3/5] Build FAILED after {time.monotonic() - t:.0f}s", file=sys.stderr, flush=True)
            return False
    else:
        fd, logfile = tempfile.mkstemp(suffix=".log")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as out:
                code = subprocess.call(cmd, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
            if code != 0:
                with open(logfile, encoding="utf-8", errors="replace") as f:
                    tail = f.read().splitlines()[-60:]
                print("\n".join(tail), file=sys.stderr)
                print(f"[3/5] Build FAILED after {time.monotonic() - t:.0f}s", file=sys.stderr, flush=True)
                return False
        finally:
            os.remove(logfile)
    log(f"[3/5] Build OK ({time.monotonic() - t:.0f}s)")
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
    ap.add_argument("--repdb-source", metavar="PATH",
                    help="Biblioteca_Ejercicios_Free folder to import (else repdb.source in local.properties, "
                         "else REPDB_SOURCE).")
    ap.add_argument("--reimport", action="store_true", help="Import the RepDB bundle even if already imported.")
    ap.add_argument("--verbose", action="store_true", help="Stream the Gradle output live.")
    args = ap.parse_args(argv)

    adb = find_adb()
    if not adb:
        print("error: adb not found. Add it to PATH or set ANDROID_HOME / ANDROID_SDK_ROOT "
              "(platform-tools).", file=sys.stderr)
        return 1

    if not args.no_build:
        props = read_local_properties(os.path.join(ROOT, "local.properties"))
        source = resolve_repdb_source(args.repdb_source, props, os.environ)
        if not (step_prod_config() and step_repdb(source, args.reimport) and build(args.verbose)):
            return 1
    else:
        log("[1/5] [2/5] [3/5] Skipped (--no-build)")

    log("[4/5] Looking for the APK...")
    apks = sorted(glob.glob(os.path.join(ROOT, APK_DIR, "*.apk")), key=os.path.getmtime, reverse=True)
    if not apks:
        print(f"error: no APK found under {APK_DIR}", file=sys.stderr)
        return 1
    apk = apks[0]  # newest
    if len(apks) > 1:
        print(f"warning: {len(apks)} APKs found; using the newest.", file=sys.stderr)
    log(f"[4/5] APK: {apk} ({os.path.getsize(apk) / (1024 * 1024):.1f} MB)")

    log("[5/5] Looking for devices and installing...")
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
        log("[5/5] No physical device connected.")
        log(f'Install later with: adb -s <serial> install -r "{apk}"')
        return 0

    fail = 0
    for s in serials:
        mcode, mout, _ = run(adb, "-s", s, "shell", "getprop", "ro.product.model")
        model = mout if mcode == 0 and mout else "unknown model"
        log(f"[5/5] Installing on {s} ({model})...")
        t0 = time.monotonic()
        code, so, se = run(adb, "-s", s, "install", "-r", apk)
        out = "\n".join(x for x in (so, se) if x)
        if code == 0 and "Success" in out:
            log(f"OK     {s} ({model}) ({time.monotonic() - t0:.1f}s)")
        else:
            print(f"FAILED {s} ({model})", file=sys.stderr)
            print(out, file=sys.stderr)
            fail = 1
    return fail


if __name__ == "__main__":
    sys.exit(main())
