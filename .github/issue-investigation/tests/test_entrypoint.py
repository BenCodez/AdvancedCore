"""Offline policy checks for the thin AdvancedCore caller; no model/API calls."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / "workflows" / "investigate-issue.yml"
PIN = "68cd96bce3684462baffa569a26295965af36339"


def validate(text):
    triggers = text.split("\non:\n", 1)[1].split("\npermissions:", 1)[0]
    if re.findall(r"^  ([A-Za-z_]+):", triggers, re.M) != ["workflow_dispatch"]:
        raise ValueError("Only manual dispatch is allowed")
    for required in (
        "github.repository == 'BenCodez/AdvancedCore'",
        "github.ref == 'refs/heads/master'",
        "github.event_name == 'workflow_dispatch'",
        "uses: BenCodez/VotingPlugin/.github/workflows/investigate-issue-reusable.yml@" + PIN,
        "openai_project_id: ${{ vars.OPENAI_PROJECT_ID }}",
        "OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}",
    ):
        if required not in text:
            raise ValueError("Caller binding changed")
    jobs = text.split("\njobs:\n", 1)[1]
    if re.findall(r"^  (\w+):$", jobs, re.M) != ["investigate"]:
        raise ValueError("Only the shared investigation job is allowed")
    if len(re.findall(r"^    uses:", jobs, re.M)) != 1 or "run:" in jobs or "steps:" in jobs:
        raise ValueError("No local engine or additional execution")
    if re.findall(r"^      (\w+):", jobs, re.M) != [
        "issue_number", "timeout_minutes", "openai_project_id", "OPENAI_API_KEY"
    ]:
        raise ValueError("Only the explicit shared interface is allowed")
    if "write" in text or "inherit" in text or "post_comment" in text:
        raise ValueError("Caller must not publish or inherit credentials")
    if re.findall(r"^  (actions|contents|issues): (\w+)$", text, re.M) != [
        ("actions", "read"), ("contents", "read"), ("issues", "read")
    ]:
        raise ValueError("Read-only permissions required")
    if re.findall(r"^      (\w+):$", triggers, re.M) != ["issue_number", "timeout_minutes"]:
        raise ValueError("Issue data cannot choose execution policy")
    if "default: '15'" not in triggers:
        raise ValueError("Bounded manual default required")


class EntrypointTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text()

    def test_exact_manual_no_post_caller(self):
        validate(self.text)

    def test_automatic_event_rejected(self):
        with self.assertRaises(ValueError):
            validate(self.text.replace("  workflow_dispatch:", "  issues:\n    types: [opened]\n  workflow_dispatch:"))

    def test_mutable_or_changed_pin_rejected(self):
        for changed in ("master", "0" * 40):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                validate(self.text.replace(PIN, changed))

    def test_issue_write_and_inherited_secrets_rejected(self):
        for changed in (
            self.text.replace("issues: read", "issues: write"),
            self.text.replace("    secrets:", "    secrets: inherit\n    unused:"),
        ):
            with self.assertRaises(ValueError):
                validate(changed)

    def test_post_option_and_ref_input_rejected(self):
        for added in ("post_comment", "engine_ref"):
            with self.subTest(added=added), self.assertRaises(ValueError):
                validate(self.text.replace("    inputs:", "    inputs:\n      " + added + ":\n        type: string"))

    def test_extra_job_or_execution_rejected(self):
        for extra in (
            "\n  extra:\n    runs-on: ubuntu-latest\n",
            "\n    steps:\n      - run: echo unsafe\n",
        ):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                validate(self.text + extra)

    def test_operator_key_name_and_canonical_caller_preserved(self):
        for old, new in (
            ("OPENAI_API_KEY", "OTHER_KEY"),
            ("BenCodez/AdvancedCore", "BenCodez/Other"),
        ):
            with self.subTest(old=old), self.assertRaises(ValueError):
                validate(self.text.replace(old, new))


if __name__ == "__main__":
    unittest.main()
