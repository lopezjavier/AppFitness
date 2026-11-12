"""Find Git Bash and a JDK for the scripts that shell out to Gradle (shared helper).

On Windows with WSL installed, a bare `bash` resolves to the WSL launcher (C:\\Windows\\System32\\bash.exe), which does not
see JAVA_HOME and fails with "JAVA_HOME is not set". Scripts load this file by path (importlib), because CI runs
`python3 -I` and that drops the script directory from sys.path.
"""
import ntpath
import os
import posixpath
import shutil

_WSL_LAUNCHER_MARKERS = ("\\windows\\system32\\", "\\windows\\sysnative\\", "\\windowsapps\\")


class BashNotFound(Exception):
    """No usable bash was found."""


def _is_wsl_launcher(path):
    lowered = path.replace("/", "\\").lower()
    return any(marker in lowered for marker in _WSL_LAUNCHER_MARKERS)


def find_bash(env=os.environ, which=shutil.which, exists=os.path.exists, is_windows=os.name == "nt"):
    """Path of a bash that inherits the environment. On Windows, never the WSL launcher."""
    if not is_windows:
        found = which("bash")
        if found:
            return found
        raise BashNotFound("bash not found on PATH")
    override = env.get("GIT_BASH")
    if override and exists(override):
        return override
    candidates = []
    git = which("git")
    if git:
        root = ntpath.dirname(ntpath.dirname(git))
        for base in (root, ntpath.dirname(root)):
            candidates += [ntpath.join(base, "bin", "bash.exe"), ntpath.join(base, "usr", "bin", "bash.exe")]
    for var, suffix in (("ProgramFiles", ""), ("ProgramFiles(x86)", ""), ("LocalAppData", "Programs")):
        base = env.get(var)
        if base:
            candidates.append(ntpath.join(base, suffix, "Git", "bin", "bash.exe"))
    for candidate in candidates:
        if exists(candidate):
            return candidate
    found = which("bash")
    if found and not _is_wsl_launcher(found):
        return found
    raise BashNotFound("Git Bash not found (plain `bash` is the WSL launcher): install Git for Windows or set GIT_BASH")


def java_env(env=os.environ, which=shutil.which, exists=os.path.exists):
    """{"JAVA_HOME": ...} when JAVA_HOME is unset and a JDK is found, else {}. A set JAVA_HOME is never overridden."""
    if env.get("JAVA_HOME"):
        return {}
    program_files = env.get("ProgramFiles")
    if program_files:
        jbr = ntpath.join(program_files, "Android", "Android Studio", "jbr")
        if exists(jbr):
            return {"JAVA_HOME": jbr}
    java = which("java")
    if java:
        pathmod = ntpath if "\\" in java else posixpath
        return {"JAVA_HOME": pathmod.dirname(pathmod.dirname(java))}
    return {}
