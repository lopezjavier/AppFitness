#!/usr/bin/env python3
"""Start the local Supabase with the rich dummy user and run the `local` app on the emulator (T-278, US-131).

Steps: preflight, `supabase start`, `supabase db reset`, apply supabase/seed-review.sql, install the local flavor on the
emulator and launch it. Local only: nothing here touches the production project.
"""
import argparse
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
IS_WINDOWS = os.name == "nt"

_spec = importlib.util.spec_from_file_location("deliver_apk", os.path.join(os.path.dirname(__file__), "deliver-apk.py"))
deliver = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(deliver)

_spec = importlib.util.spec_from_file_location("gitbash", os.path.join(os.path.dirname(__file__), "gitbash.py"))
gitbash = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gitbash)

SEED_FILE =os.path.join("supabase", "seed-review.sql")
CHECK_FILE = os.path.join("supabase", "review", "seed-review.check.sql")
CONFIG_TOML = os.path.join("supabase", "config.toml")
APP_ID = "io.github.lopezjavier.appfitness.local"
# Local-only credentials of supabase/seed.sql (planning/08-environment-setup.md section 3.2); not secrets.
TEST_EMAIL = "test@appfitness.local"
TEST_PASSWORD = "appfitness-local-test"
TOTAL_STEPS = 6


def parse_project_id(toml_text):
    """`project_id` of supabase/config.toml; the Docker containers are named supabase_<kind>_<project_id>."""
    match = re.search(r'^\s*project_id\s*=\s*"([^"]+)"', toml_text, re.MULTILINE)
    if not match:
        raise ValueError("project_id not found in supabase/config.toml")
    return match.group(1)


def pick_emulator(devices, serial=None):
    """The emulator to use. `serial` (from --serial) wins; else exactly one emulator in state `device` is needed."""
    if serial:
        for s, state in devices:
            if s == serial:
                if state != "device":
                    raise ValueError(f"{serial} is {state}, not ready")
                return serial
        raise ValueError(f"{serial} is not connected (adb devices)")
    emulators = [s for s, state in devices if state == "device" and s.startswith("emulator-")]
    if not emulators:
        raise ValueError("no emulator is running: start one in Android Studio (a phone is never used here)")
    if len(emulators) > 1:
        raise ValueError(f"several emulators are running ({', '.join(emulators)}): pass --serial ID")
    return emulators[0]


def missing_local_config(props):
    """Keys of local.properties the local build needs and that are blank. The URL has a default in the build."""
    return [key for key in ("supabase.local.publishableKey",) if not props.get(key, "").strip()]


def psql_command(container):
    return ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1"]


def build_steps(args, serial, project_id):
    """The commands of the run, in order: dicts with step (1..6), name, cmd, stdin (a file or None), env."""
    container = f"supabase_db_{project_id}"
    gradlew = os.path.join(ROOT, "gradlew.bat" if IS_WINDOWS else "gradlew")
    steps = [{"step": 2, "name": "start the local Supabase", "cmd": ["npx", "supabase", "start"]}]
    if not args.no_reset:
        steps.append({"step": 3, "name": "reset the database", "cmd": ["npx", "supabase", "db", "reset"]})
    steps.append({"step": 4, "name": "apply the rich dataset", "cmd": psql_command(container), "stdin": SEED_FILE})
    if args.check:
        steps.append({"step": 4, "name": "check the dataset", "cmd": psql_command(container), "stdin": CHECK_FILE})
    if not args.no_build:
        steps.append({"step": 5, "name": "install the local flavor", "cmd": [gradlew, "-q", ":androidApp:installLocalDebug"],
                      "env": {"ANDROID_SERIAL": serial}})
    steps.append({"step": 5, "name": "grant local network access (Android 17+; ignored before)",
                  "cmd": ["adb", "-s", serial, "shell", "pm", "grant", APP_ID, "android.permission.ACCESS_LOCAL_NETWORK"],
                  "optional": True})
    steps.append({"step": 5, "name": "launch the app",
                  "cmd": ["adb", "-s", serial, "shell", "monkey", "-p", APP_ID, "-c",
                          "android.intent.category.LAUNCHER", "1"]})
    return steps


def credentials_line():
    return f"Tap Test login: {TEST_EMAIL} / {TEST_PASSWORD}"


def log(message, stream=None):
    print(message, file=stream or sys.stdout, flush=True)


def fail(message):
    print(f"error: {message}", file=sys.stderr, flush=True)
    return 1


def preflight(args, adb):
    """Returns (serial, None) or (None, error message). Nothing is started before this passes."""
    if shutil.which("docker") is None or subprocess.run(["docker", "info"], capture_output=True).returncode != 0:
        return None, "Docker is not running: start Docker Desktop and retry"
    if shutil.which("npx") is None:
        return None, "npx not found: install Node.js and run `npm install`"
    if not adb:
        return None, "adb not found: add platform-tools to PATH or set ANDROID_HOME / ANDROID_SDK_ROOT"
    code = subprocess.run([adb, "devices"], capture_output=True, text=True, errors="replace")
    try:
        serial = pick_emulator(deliver.parse_devices(code.stdout), args.serial)
    except ValueError as e:
        return None, str(e)
    if not args.no_build:
        if not os.environ.get("JAVA_HOME") and not gitbash.java_env():
            return None, ("JAVA_HOME is not set and no JDK was found (Android Studio jbr or java on PATH): "
                          "set JAVA_HOME to a JDK 21")
        missing =missing_local_config(deliver.read_local_properties(os.path.join(ROOT, "local.properties")))
        if missing:
            return None, ("local.properties is missing " + ", ".join(missing) +
                          " (the publishable key printed by `npx supabase start`; see planning/08-environment-setup.md)")
        problem = deliver.repdb_bundle_problem()
        if problem:
            return None, problem
    return serial, None


def resolve(cmd, adb):
    if cmd[0] == "adb":
        return [adb, *cmd[1:]]
    if cmd[0] in ("npx", "docker"):  # npx is npx.cmd on Windows: resolve it through PATH
        return [shutil.which(cmd[0]) or cmd[0], *cmd[1:]]
    return cmd


def run_step(step, adb, verbose):
    cmd = resolve(step["cmd"], adb)
    # gradlew.bat fails with "JAVA_HOME is not set" when it is unset and no java is on PATH: pass a discovered JDK.
    env = dict(os.environ, **gitbash.java_env(), **step.get("env", {}))
    stdin = open(os.path.join(ROOT, step["stdin"]), "rb") if step.get("stdin") else None
    try:
        if verbose:
            p = subprocess.run(cmd, cwd=ROOT, env=env, stdin=stdin)
            return p.returncode, ""
        p = subprocess.run(cmd, cwd=ROOT, env=env, stdin=stdin, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, errors="replace")
        return p.returncode, p.stdout
    finally:
        if stdin:
            stdin.close()


def main(argv=None):
    ap = argparse.ArgumentParser(prog="review-emulator.py", description=__doc__)
    ap.add_argument("--no-build", action="store_true", help="Do not build or install: only relaunch the installed app.")
    ap.add_argument("--no-reset", action="store_true", help="Skip `db reset` (the dataset deletes its own rows first).")
    ap.add_argument("--serial", metavar="ID", help="The emulator to use when several run.")
    ap.add_argument("--dry-run", action="store_true", help="Print the commands and exit without running anything.")
    ap.add_argument("--check", action="store_true", help="Also run the pgTAP checks of the dataset after applying it.")
    ap.add_argument("--verbose", action="store_true", help="Stream the output of every command.")
    args = ap.parse_args(argv)

    try:
        with open(os.path.join(ROOT, CONFIG_TOML), encoding="utf-8") as f:
            project_id = parse_project_id(f.read())
    except (OSError, ValueError) as e:
        return fail(str(e))

    if args.dry_run:
        for s in build_steps(args, args.serial or "<emulator>", project_id):
            stdin = f" < {s['stdin']}" if s.get("stdin") else ""
            log(f"[{s['step']}/{TOTAL_STEPS}] {s['name']}: {' '.join(s['cmd'])}{stdin}")
        log(f"[{TOTAL_STEPS}/{TOTAL_STEPS}] {credentials_line()}")
        return 0

    adb = deliver.find_adb()
    log(f"[1/{TOTAL_STEPS}] Preflight (Docker, npx, adb, emulator, local.properties)...")
    serial, problem = preflight(args, adb)
    if problem:
        return fail(problem)
    log(f"[1/{TOTAL_STEPS}] OK, emulator {serial}")

    for s in build_steps(args, serial, project_id):
        label = f"[{s['step']}/{TOTAL_STEPS}] {s['name']}"
        log(label + "...")
        t = time.monotonic()
        code, output = run_step(s, adb, args.verbose)
        if code != 0 and s.get("optional"):
            log(f"{label}: skipped (exit {code}, normal on Android before 17)")
            continue
        if code != 0:
            tail = "\n".join(output.splitlines()[-30:])
            if tail:
                print(tail, file=sys.stderr)
            return fail(f"{s['name']} failed (exit {code})")
        log(f"{label}: OK ({time.monotonic() - t:.0f}s)")

    log(f"[{TOTAL_STEPS}/{TOTAL_STEPS}] {credentials_line()}")
    log("Then look at: Diario, Peso, Entreno, Trainer, Informes and the Centro de avisos "
        "(a weekly summary, the weight, circumference and check-in reminders).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
