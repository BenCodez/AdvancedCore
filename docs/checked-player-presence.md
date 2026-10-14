# Checked server player presence

`AdvancedCorePlugin.checkPlayerPresenceAsync(UUID)` reads server join-history evidence for an already resolved UUID.
It returns `CompletionStage<PlayerPresence>` with `JOINED`, `NEVER_JOINED` or `UNKNOWN`. It does not resolve a name,
create a user, write history or use plugin-row existence as join proof. Existing boolean identity-validation APIs
remain unchanged. Resolve the correct current server UUID on an appropriate worker before calling this API;
an unresolved or ambiguous migrated identity must remain unknown rather than being guessed from a name.

`checkPlayerPresenceIdentitiesAsync(Collection<UUID>)` accepts up to eight verified current/historical UUIDs
belonging to the same person. After input validation, positive evidence from any supplied identity is sufficient. Negative evidence
requires every supplied identity to be confirmed negative; uncertain, empty, missing or oversized input stays
unknown. The API does not discover aliases or validate their ownership. Supply a null member to represent an
unresolved part of a migration, rather than silently omitting it: this makes the complete request unknown before
any read is queued. Duplicate UUIDs are read only once.

The requesting thread performs no history, filesystem or UUID lookup. The existing AdvancedCore storage executor
owns the read, with at most 128 admitted entries per service. An alias request atomically reserves one entry
for its public aggregate plus one for each distinct child. Insufficient capacity rejects the complete request
as unknown without scheduling partial alias reads. Missing identity, saturation, executor rejection,
provider failure and retired requests return `UNKNOWN`. Completions may run on a worker, requesting or retiring
thread: use an asynchronous continuation or the correct platform scheduler for client work. Never attach
blocking storage or player effects directly. A result is an inspection snapshot, not permission to mutate a
player or proof that a later menu/configuration generation is still current.

The runtime captures server-wide player-storage directory metadata during enable on the platform owner.
Recognized CraftBukkit implementations provide either the level-directory API or the player's storage-directory
metadata; a world/dimension folder is never assumed to own player data. Unsupported layouts remain `UNKNOWN`.
The worker reads UUID-named history directly without calling the server's offline-player loader, profile lookup,
data fixer or migration logic. Complete GZIP/NBT records supply `JOINED`; malformed or incomplete records remain
`UNKNOWN`. Current, recovery and retained migration files are read without creating, moving or rewriting them.
Input is bounded to 1 MiB compressed, 8 MiB expanded, depth 64 and 100,000 structural nodes; oversized history is
unknown. Negative evidence requires a readable unchanged directory with no record or recovery file for that UUID.
Native interrupted-save files (`UUID-random.dat`) and corruption backups prevent negative answers even when no
canonical record survives; their contents are never read as positive proof or changed. A worker-owned directory
index preserves retained recovery evidence across checks. Every absent result requires a fresh bounded
enumeration: directory identity and timestamps alone cannot certify unchanged entries.
Scanning is bounded to 1,000,000 entries and 65,536 recovery identities; incomplete, failed or unstable scans
remain unknown. Closing the lookup clears the index without waiting for an in-flight scan.
File keys strengthen stability checks when the provider supplies them. Providers without file keys use
unchanged creation timestamps, modification times and record sizes; unavailable creation metadata remains
unknown. A stable valid record provides positive evidence without inspecting later siblings. A failed
candidate read can prevent negative evidence but cannot erase a readable valid sibling. Directory readability
is rechecked before using an absence index. A missing directory, symbolic link, failed scope read or changed
scope remains unknown. No raw history or paths are
returned or logged.

The additive `checkPlayerPresenceIdentitiesAsync(identities, true)` operation also checks whether the
actual history scope contains unmapped offline/non-online UUID records or observed joins. Current-name
cache mappings cannot enumerate all former offline names. Such retained evidence prevents a negative
online-family result unless a checked alias already supplies positive history. A stable readable scope
containing only online UUID records can still supply `NEVER_JOINED`; malformed offline records and
interrupted/corruption backups keep the result unknown. This uses the same bounded worker-owned index
and admission limits, with fresh bounded enumeration before each wider negative result because directory
timestamps cannot certify unchanged contents. Online UUIDs require version 4 and variant 2; unsupported
aliases retain positives but cannot certify wider absence. No profile lookup or history mutation is used. Existing UUID-only methods retain
their behavior. Providers that implement only the original lookup method preserve positives but
cannot certify the wider negative scope.

A generation-owned join listener and initial UUID-only online snapshot supply positive evidence for players whose
first history file has not yet been saved. The requesting/worker threads never inspect player entity state.
This evidence retains at most 65,536 joined UUIDs; failed snapshot capture or capacity exhaustion disables new
negative answers while retained positive evidence remains usable. Retired listeners cannot repopulate the old
generation. Alias requests share the same admission/retirement boundary as individual reads: an unfinished
aggregate becomes `UNKNOWN` at retirement even if one alias already completed positive.

These are checks against the server's retained history for the resolved identity. They cannot reconstruct
deleted history or unrelated UUIDs after an identity-mode change. Keep uncertain migrated identities visible.
Shutdown retires requests before existing cleanup shuts down the shared worker. Re-enable retires the previous
service; delayed reads do not publish into its replacement. The service creates no executor or alternate user
storage and does not replace existing lifecycle cleanup.

An optional display filter must keep both `JOINED` and `UNKNOWN`, and exclude only confirmed `NEVER_JOINED`.
Filtering must not change award eligibility. Exact consumer integration and live platform validation are
separate acceptance checks; passing the library's focused tests does not prove either.
