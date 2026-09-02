#!/usr/bin/env python3
"""Apply / revert the per-version app-side compatibility patches needed to build ExampleApp
against old Embrace SDKs, plus set and verify the version pin.

Recipes are DATA (see RECIPES) and came from an actual sweep back to 6.14.0 on a modern AGP
toolchain - re-verify them when the app's AGP/Gradle/Kotlin move. Every patch is recorded to a
journal so --revert-all restores the tree even if a run dies mid-cell.

Usage:
  python3 compat_patch.py --version 7.5.0 --apply
  python3 compat_patch.py --version 7.5.0 --apply --verify     # also compile-check
  python3 compat_patch.py --revert-all
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

def repo_root():
    """Locate the SDK repo without hardcoding a home directory."""
    cp = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                        timeout=60, cwd=str(pathlib.Path(__file__).resolve().parent))
    if cp.returncode == 0 and cp.stdout.strip():
        return pathlib.Path(cp.stdout.strip())
    return pathlib.Path(__file__).resolve().parents[3]


REPO = repo_root()
EXAMPLE = REPO / "examples/ExampleApp"
CATALOG = EXAMPLE / "gradle/libs.versions.toml"
JOURNAL = EXAMPLE / ".vfm-compat-journal.json"

# Each recipe: what differs from the modern build. Keep patches minimal - every extra edit is a
# difference between arms that is NOT the SDK.
RECIPES = {
    "modern": {
        "applies_to": ">=8.0",
        "plugin_id": "io.embrace.gradle",
        "extra_deps": [],
        "notes": "no patch needed",
    },
    "7x": {
        "applies_to": "7.0-7.9.x",
        "plugin_id": "io.embrace.swazzler",
        "extra_deps": ["io.embrace:embrace-android-fcm:{version}"],
        "notes": ("swazzler plugin DOES apply on modern AGP; embrace-android-fcm must be declared "
                  "explicitly because later versions bundle it transitively"),
    },
    "6x": {
        "applies_to": "<=6.14.x",
        "plugin_id": None,   # plugin-less: swazzler cannot apply on modern AGP
        "extra_deps": [],
        "notes": ("build WITHOUT the plugin: hand-inject config resources and satisfy the no-appId "
                  "path with exporters. Mildly favourable to 6.x - label it in the report"),
    },
}


def recipe_for(version):
    if version == "local":
        return "modern"
    major = int(version.split(".")[0])
    if major >= 8:
        return "modern"
    if major == 7:
        return "7x"
    return "6x"


def read_journal():
    return json.loads(JOURNAL.read_text()) if JOURNAL.exists() else {"files": {}, "version": None}


def write_journal(j):
    JOURNAL.write_text(json.dumps(j, indent=1))


def snapshot(path, journal):
    key = str(path.relative_to(REPO))
    if key not in journal["files"]:
        journal["files"][key] = path.read_text()


def set_pin(version, journal):
    snapshot(CATALOG, journal)
    text = CATALOG.read_text()
    if version == "local":
        gp = (REPO / "gradle.properties").read_text()
        version = next(l.split("=", 1)[1].strip() for l in gp.splitlines()
                       if l.startswith("version="))
    new = re.sub(r'^embrace = ".*"$', f'embrace = "{version}"', text, flags=re.M)
    if new == text and f'"{version}"' not in text:
        sys.exit("could not rewrite the embrace pin - inspect the catalog format")
    CATALOG.write_text(new)
    return version


def apply_recipe(version, journal):
    name = recipe_for(version)
    recipe = RECIPES[name]
    print(f"recipe {name} for {version}: {recipe['notes']}")
    resolved = set_pin(version, journal)
    journal["version"] = version

    # plugin id swap (7.x) or plugin removal (6.x) in the app's build file
    app_build = EXAMPLE / "app/build.gradle.kts"
    if recipe["plugin_id"] != "io.embrace.gradle":
        snapshot(app_build, journal)
        text = app_build.read_text()
        if recipe["plugin_id"] is None:
            text = re.sub(r'^\s*(alias\(libs\.plugins\.embrace\)|id\("io\.embrace\.[^"]+"\)).*$',
                          "    // [vfm] plugin intentionally omitted for 6.x plugin-less build",
                          text, flags=re.M)
        else:
            text = re.sub(r'^\s*(alias\(libs\.plugins\.embrace\)|id\("io\.embrace\.gradle"\)).*$',
                          f'    id("{recipe["plugin_id"]}")', text, flags=re.M)
        for dep in recipe["extra_deps"]:
            dep = dep.format(version=resolved)
            if dep.split(":")[1] not in text:
                text = text.replace("dependencies {", f'dependencies {{\n    implementation("{dep}")', 1)
        app_build.write_text(text)
        print(f"patched {app_build.relative_to(REPO)}")

    write_journal(journal)
    print(f"pin set to {resolved}; journal has {len(journal['files'])} snapshot(s)")
    if recipe["plugin_id"] is None:
        print("REMINDER: 6.x needs hand-injected config resources + exporter setup; see "
              "references/version-compat.md before trusting the build")


def revert_all():
    journal = read_journal()
    if not journal["files"]:
        print("nothing to revert")
        return
    for rel, content in journal["files"].items():
        (REPO / rel).write_text(content)
        print(f"reverted {rel}")
    JOURNAL.unlink(missing_ok=True)
    print("journal cleared - now confirm `git status --short` shows only intended changes")


def verify_build():
    print("compile-checking the patched tree (assembleBenchmark)...")
    cp = subprocess.run([str(EXAMPLE / "gradlew"), "-p", str(EXAMPLE), ":app:assembleBenchmark", "-q"],
                        capture_output=True, text=True, timeout=1800)
    if cp.returncode != 0:
        print(cp.stdout[-2000:])
        print(cp.stderr[-2000:])
        sys.exit("VERIFY FAILED: patched tree does not build - fix the recipe before running cells")
    print("verify OK: patched tree builds")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--revert-all", action="store_true")
    args = ap.parse_args()

    if args.revert_all:
        revert_all()
        return
    if not (args.version and args.apply):
        sys.exit("use --version X --apply, or --revert-all")
    apply_recipe(args.version, read_journal())
    if args.verify:
        verify_build()


if __name__ == "__main__":
    main()
