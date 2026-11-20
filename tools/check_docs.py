"""Validate canonical requirement, route and scenario references without dependencies."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8-sig")


def check():
    errors = []
    requirements = read("docs/requirements/SRS.md")
    scenarios = read("docs/test-strategy/SCENARIOS.md")
    contract = read("docs/design/API_CONTRACT.md")
    frs = re.findall(r"^\| (FR-\d+) \|", requirements, re.M)
    tcs = re.findall(r"^\| (TC-\d+) \|", scenarios, re.M)
    rules = set(re.findall(r"^\| (RULE-[A-Z]+-\d+) \|", read("docs/requirements/BUSINESS_RULES.md"), re.M))
    routes = re.findall(r"^\| (GET|POST|PUT|PATCH|DELETE) \| ([^|]+) \|", contract, re.M)
    for label, values in (("requirements", frs), ("scenarios", tcs), ("routes", routes)):
        if not values or len(values) != len(set(values)):
            errors.append(f"Missing or duplicate {label}")
    for fr in frs:
        if fr not in scenarios or fr not in contract:
            errors.append(f"Unmapped requirement: {fr}")
    for reference in set(re.findall(r"FR-\d+", scenarios + contract)):
        if reference not in frs:
            errors.append(f"Unknown requirement: {reference}")
    for reference in set(re.findall(r"RULE-[A-Z]+-\d+", scenarios)):
        if reference not in rules:
            errors.append(f"Unknown rule: {reference}")
    for br in range(1, 11):
        if f"BR-{br:02}" not in requirements:
            errors.append(f"Missing BR-{br:02} acceptance criteria")
    for path in [ROOT / name for name in ("AGENTS.md", "TASKS.md", "README.md", "CODEX_START_HERE.md")]+list((ROOT / "docs").rglob("*.md")):
        body = path.read_text(encoding="utf-8-sig")
        if any(line != line.rstrip() for line in body.splitlines()):
            errors.append(f"Trailing whitespace: {path.relative_to(ROOT)}")
        if re.search(r"[\u0102\u0103\u0110\u0111\u01a0\u01a1\u01af\u01b0\u1ea0-\u1ef9]", body):
            errors.append(f"Non-English project prose: {path.relative_to(ROOT)}")
    if list(ROOT.glob("*.zip")) or (ROOT / ".import-staging").exists() or (ROOT / "quizmaster-codex-autonomous-starter").exists():
        errors.append("Starter import artifacts remain")
    if errors:
        print("\n".join(f"FAIL: {error}" for error in errors))
        return 1
    print(f"PASS: {len(frs)} requirements, {len(routes)} unique routes, {len(tcs)} scenarios; references and import layout valid")
    return 0


if __name__ == "__main__":
    sys.exit(check())
