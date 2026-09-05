#!/usr/bin/env python3
"""Rebuild the bench maxims ledger from every campaign archive kept under records/campaigns/ (<run-id>.zip).

The ledger is derived state: this script is its definition. Run it after a maxim's rule changes in a way
that must be re-scored over the historical evidence, or to add a campaign whose cell the provenance
cannot name. Ordinary campaigns are scored once, as they happen, with `tools/startup maxims score`, and
then appended to RUNS here so the rebuild stays complete. Deletes the ledger first, so the result is
exactly these runs and nothing else, then re-renders MAXIMS.md.

Cells whose run-metadata.json / cell-state.json name the device, SDK version and arm pass no overrides;
the August 2026 campaigns predate that provenance, and the user-session arms carry their cohort as the
arm label.
"""
import pathlib
import subprocess

HERE = pathlib.Path(__file__).resolve().parent
RECORDS = HERE.parent  # .claude/skills/_shared/records
REPO = RECORDS.parents[3]
TOOL = REPO / "tools" / "startup"
LEDGER = HERE / "ledger.json"
CAMPAIGNS = RECORDS / "campaigns"
REF = RECORDS / "longitudinal" / "reference-set.json"
AUGUST_SDK = "9.2.0-SNAPSHOT-0811"

# (campaign directory under records/campaigns, {override flag: value})
RUNS = [
    ("campaign-2026-08-11", {"--device-key": "mid-a", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("a14-campaign2-2026-08-11", {"--device-key": "mid-a", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("pixel-campaign-2026-08-11", {"--device-key": "mid-b", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("a01-campaign-2026-08-11", {"--device-key": "entry-a", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("p7p-campaign-2026-08-12", {"--device-key": "flagship-a", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("p7p-cooldown-2026-08-12", {"--device-key": "flagship-a", "--sdk-version": AUGUST_SDK, "--arm": "default-cool"}),
    ("cooldown-2026-08-12-pixel", {"--device-key": "mid-b", "--sdk-version": AUGUST_SDK, "--arm": "default-cool"}),
    ("cooldown-2026-08-12-a14", {"--device-key": "mid-a", "--sdk-version": AUGUST_SDK, "--arm": "default-cool"}),
    ("cooldown-2026-08-12-a01", {"--device-key": "entry-a", "--sdk-version": AUGUST_SDK, "--arm": "default-cool"}),
    ("a01-mini-2026-08-12", {"--device-key": "entry-a", "--sdk-version": AUGUST_SDK, "--arm": "default"}),
    ("2026-08-26-port-validation-mid-b", {}),
    ("2026-08-26-port-side-by-side-mid-b", {}),
    ("2026-08-26-port-vfm-mid-b-9.2.0-reference", {"--sdk-version": "9.2.0"}),
    ("2026-09-03-first-session-fix-arm-a-9.2.0", {"--arm": "new-user-session"}),
    ("2026-09-03-first-session-fix-arm-b-fix", {"--arm": "new-user-session"}),
    ("2026-09-03-first-session-fix-arm-c-expired-hook", {"--arm": "expired-user-session"}),
    ("2026-09-03-first-session-fix-arm-d-expired-hook-fix", {"--arm": "expired-user-session"}),
    ("2026-09-05-paired-pixel3-kotlin", {}),
    ("2026-09-05-paired-pixel3-python", {"--device-key": "mid-b", "--sdk-version": "9.2.0", "--arm": "full"}),
]


def main():
    if LEDGER.exists():
        LEDGER.unlink()
    for run_id, overrides in RUNS:
        args = [str(TOOL), "maxims", "score", str(CAMPAIGNS / f"{run_id}.zip"), "--reference-set", str(REF),
                "--ledger", str(LEDGER), "--records", str(RECORDS), "--run-id", run_id]
        for flag, value in overrides.items():
            args += [flag, value]
        subprocess.run(args, check=True)
    subprocess.run([str(TOOL), "maxims", "render", "--ledger", str(LEDGER), "-o", str(HERE / "MAXIMS.md")], check=True)


if __name__ == "__main__":
    main()
