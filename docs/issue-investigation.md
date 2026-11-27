# Manual issue investigation

The **Investigate issue without posting** workflow is a manual entrypoint to the
shared VotingPlugin investigator. It has no issue, push or scheduled trigger and
never posts a public issue comment. The caller pins the shared workflow to an
immutable revision; it does not copy the engine into AdvancedCore.

## Before activation

Merging the caller does not provision or verify hosted prerequisites. An
authorized custodian must verify the existing configuration and protection rules:

- The existing secret name is `OPENAI_API_KEY`. No key is created by this change.
- `OPENAI_PROJECT_ID` must identify the approved project.
- `ISSUE_INVESTIGATION_POLICY_URL` and
  `ISSUE_INVESTIGATION_DISCLOSURE_SHA256` must identify the approved policy.
- The shared runner's budget, persistent claim ledger, artifact storage,
  authentication and cleanup requirements must be qualified for this repository.

Do not infer missing credentials from an inventory permission error. The caller
passes only the named key, with read-only Actions, contents and issue permissions.
It does not use inherited secrets or grant issue-write permission. Issue text
cannot select the shared revision, project or workflow command.

Only an independently approved, bounded hosted smoke run can establish actual
Actions provenance, configuration, model accounting and artifact receipt behavior.
Local fixtures and a merged workflow are not hosted acceptance.

## Manual use after approval

Run from the default `master` branch using **Actions → Investigate issue without
posting → Run workflow**. Enter an AdvancedCore issue number and a runtime limit
between 5 and 30 minutes; the default is 15. This limit is not a dollar cap.
There is no posting opt-in in this caller. A public reply requires a separately
authorized, reviewed publication route.

Successful shared runs emit outcome, artifact ID, evidence revision, source SHA
and engine SHA receipts. Reports are unreviewed analysis, not proof a defect was
reproduced. Skipped or failed admission must not be described as an investigation.
Use the exact producing run/attempt and source/engine identities when accepting
an artifact; do not substitute a report from another revision.

## AdvancedCore evidence and builds

AdvancedCore is a library embedded by plugins. Establish the reported consumer,
AdvancedCore version and actual loaded artifact before attributing runtime
behavior. Compare reported-version source separately from current development
source. A SNAPSHOT label or Maven dependency declaration does not identify JAR
bytes. SimpleAPI is a dependency and VotingPlugin is a consumer; verify their
actual revisions and artifacts independently.

The project is `AdvancedCore/pom.xml` and currently builds with JDK 21:

```sh
mvn -B -f AdvancedCore/pom.xml test
mvn -B -f AdvancedCore/pom.xml package
```

Use focused tests first where appropriate. Do not use deployment, install or
developer-copy profiles for investigation. Build success does not establish
acceptance on the affected server or consumer.

See the [README](https://github.com/BenCodez/AdvancedCore/blob/master/README.md),
[release tags](https://github.com/BenCodez/AdvancedCore/tags),
[development source](https://github.com/BenCodez/AdvancedCore/tree/master) and
[reward documentation](https://github.com/BenCodez/AdvancedCore/wiki/Rewards).
Reports must pin source citations to the actual inspected revision. A source-tree
link is not a verified successful development build or downloadable JAR receipt.

## Rollback

Stop new dispatches through the authorized workflow controls. Reconcile in-flight
remote sessions, claims, artifacts and pending charges with their existing owner;
disabling the entrypoint does not prove work stopped. Preserve ambiguous claims
and deduplication/budget evidence. Do not delete or rotate credentials as part of
rollback. An approved source revert of this thin caller must leave the shared
engine and unrelated workflows unchanged.

## Local validation

Run `python3 -m unittest discover -s .github/issue-investigation/tests -v` and lint
the caller with Actionlint. Shared admission, artifact, budget and posting tests
belong to the exact pinned shared revision; caller checks do not replace them.
