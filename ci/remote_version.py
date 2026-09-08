"""Single source of truth for Remote artifact names and APK verification."""
from pathlib import Path
import os
import re
import subprocess


def read_version(text=None):
    if text is None:
        text = Path("remote/build.gradle").read_text()
    name = re.search(r"versionName\s+'([^']+)'", text).group(1)
    code = int(re.search(r"versionCode\s+(\d+)", text).group(1))
    return name, code


if __name__ == "__main__":
    name, code = read_version()
    previous = subprocess.check_output(
        ["git", "show", "HEAD^:remote/build.gradle"], text=True
    )
    previous_name, previous_code = read_version(previous)
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD^", "HEAD", "--", "remote/src/main", "remote/build.gradle"],
        text=True,
    ).strip()
    published = subprocess.run(
        ["git", "cat-file", "-e", f"HEAD^:dist/HomeSmoke_Remote_{previous_name}_Android6plus.apk"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0
    if changed:
        # Failed, unpublished candidates can be repaired under the reserved version.
        assert code >= previous_code, "Remote versionCode must never decrease"
        if published:
            assert code > previous_code, "Remote changed: increment versionCode before publishing"
            assert name != previous_name, "Remote changed: increment visible versionName before publishing"
    print(f"HomeSmoke Remote {name} (build {code})")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"name={name}\ncode={code}\n")
