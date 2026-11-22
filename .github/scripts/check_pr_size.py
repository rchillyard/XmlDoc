#!/usr/bin/env python3
"""Fail a pull request that adds an unreasonable amount of data to the repository.

GitHub hard-rejects any single file over 100 MB, but it places no limit at all on the
total size of a push, so a pull request can quietly multiply the size of the history.
That is permanent: rewriting it afterwards means a force push and a rewrite for every
clone. This gate is the cheap check that stops it at review time instead.

Two limits, both in MiB:

  --max-total  the bytes this pull request adds, summed over every file
  --max-file   the size of any one file after the change

A newly added file counts its whole size. A modified file counts only its growth, so
editing a large file that is already in the history is not penalised, and a pure
rename counts nothing. Deletions are ignored rather than credited: a pull request
cannot earn room for a large binary by deleting something else.

Exit status is 0 when the pull request is within both limits, 1 when it is not.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

MIB = 1024 * 1024
NULL_SHA = "0" * 40


def git(*args: str) -> bytes:
    """Run a git command, returning stdout and raising on a non-zero exit."""
    return subprocess.run(("git", *args), check=True, stdout=subprocess.PIPE).stdout


def changed_files(base: str, head: str) -> list[tuple[str, str, str, str]]:
    """Return (status, path, source blob, destination blob) for each file in the diff.

    `git diff --raw -z` emits a metadata token beginning with ':' followed by one path
    token, or two when a rename or copy was detected, all NUL-separated. Parsing the
    blob hashes out of that is what lets the sizes below be read straight from the
    object database, so no path is ever handed back to a shell.
    """
    out = git("diff", "--raw", "-z", base, head).decode("utf-8", "surrogateescape")
    tokens = out.split("\0")
    index = 0
    files: list[tuple[str, str, str, str]] = []
    while index < len(tokens):
        token = tokens[index]
        if not token.startswith(":"):
            index += 1
            continue
        fields = token[1:].split()
        source, destination, status = fields[2], fields[3], fields[4]
        path_count = 2 if status[0] in ("R", "C") else 1
        paths = tokens[index + 1 : index + 1 + path_count]
        index += 1 + path_count
        files.append((status[0], paths[-1], source, destination))
    return files


def blob_sizes(shas: set[str]) -> dict[str, int]:
    """Look up the size of every blob in one batch.

    Keyed by position rather than by the hash in the reply: `git diff --raw` abbreviates
    hashes and `git cat-file` answers with the full one, so matching the two up by hash
    silently finds nothing. `--batch-check` emits exactly one line per input, in order,
    which is the dependable correspondence.
    """
    wanted = sorted(sha for sha in shas if sha.strip("0"))
    if not wanted:
        return {}
    completed = subprocess.run(
        ("git", "cat-file", "--batch-check"),
        input=("\n".join(wanted) + "\n").encode(),
        stdout=subprocess.PIPE,
        check=True,
    )
    lines = completed.stdout.decode().splitlines()
    sizes: dict[str, int] = {}
    for sha, line in zip(wanted, lines):
        parts = line.split()
        if len(parts) == 3 and parts[1] == "blob":
            sizes[sha] = int(parts[2])
    return sizes


def human(size: int) -> str:
    return f"{size / MIB:.2f} MiB" if size >= MIB else f"{size / 1024:.1f} KiB"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", help="base commit, typically the merge base")
    parser.add_argument("head", help="head commit of the pull request")
    parser.add_argument("--max-total", type=float, default=25.0, help="MiB added in total")
    parser.add_argument("--max-file", type=float, default=5.0, help="MiB for any one file")
    args = parser.parse_args()

    max_total = int(args.max_total * MIB)
    max_file = int(args.max_file * MIB)

    head = git("rev-parse", args.head).decode().strip()
    merge_base = git("merge-base", args.base, head).decode().strip()
    files = changed_files(merge_base, head)
    sizes = blob_sizes({sha for _, _, source, destination in files for sha in (source, destination)})

    total = 0
    contributions: list[tuple[int, int, str]] = []
    oversized: list[tuple[int, str]] = []
    for status, path, source, destination in files:
        if status == "D":
            continue
        final = sizes.get(destination, 0)
        added = final if status == "A" else max(0, final - sizes.get(source, 0))
        total += added
        contributions.append((added, final, path))
        if final > max_file:
            oversized.append((final, path))

    report: list[str] = [
        f"Comparing {merge_base[:12]}..{head[:12]}",
        f"{len(contributions)} file(s) added or changed, adding {human(total)} "
        f"against a budget of {human(max_total)}.",
        "",
    ]
    biggest = sorted(contributions, reverse=True)[:10]
    if biggest and biggest[0][0] > 0:
        report.append("Largest contributions:")
        report.extend(f"  {human(added):>12}  {path}" for added, _, path in biggest if added > 0)
        report.append("")

    failures: list[str] = []
    if total > max_total:
        failures.append(
            f"This pull request adds {human(total)}, over the {human(max_total)} budget."
        )
    if oversized:
        failures.append(f"{len(oversized)} file(s) end up larger than {human(max_file)}:")
        failures.extend(f"  {human(size):>12}  {path}" for size, path in sorted(oversized, reverse=True))

    if failures:
        report.append("FAILED")
        report.extend(failures)
        report.append("")
        report.append(
            "If these files belong in the history, store them with Git LFS. If they are "
            "evidence or scratch output, keep them on their own branch or outside the "
            "repository and add the path to .gitignore. If this one really is an "
            "exception, add the label 'large-files-ok' to the pull request and re-run."
        )
    else:
        report.append("OK: within both limits.")

    text = "\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(f"## Pull request size\n\n```\n{text}\n```\n")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
