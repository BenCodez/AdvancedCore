# Literal reward display placeholders

Existing reward templates, actions, queues and string APIs keep their behavior.
Integrations can opt a lower-trust display value into final component rendering:

```java
options.addPlaceholder("SiteName", rawIdentifier);
options.addLiteralDisplayPlaceholder("SiteName", literalLabel, safeLegacyDisplay);
```

`RewardBuilder.withLiteralDisplayPlaceHolder` and
`RewardDisplayPlaceholders.putLiteral` expose the same three values. The raw
placeholder remains authoritative for commands, URLs and other actions. Supply
an already guarded `safeLegacyDisplay` for older embedded library readers; it
is retained in the existing v1 display metadata. An explicit administrator
label with colors should continue using `addDisplayPlaceholder`.

The new literal binding travels in the ordinary serialized reward placeholder
map, so existing delayed/offline queues preserve it. No schema migration is
needed. Replacing a literal override with a trusted display override clears the
old literal metadata, including case variants.

Player messages and reward broadcasts replace literal placeholders with opaque
printable tokens before trusted color, PlaceholderAPI, JavaScript and rich-text
processing. Provider-expanded text is protected again immediately before component parsing.
Visible text and hover text are restored only into final components.
Action binding keys are case-insensitive; conflicting case variants are rejected.
A missing raw binding inserts an empty action value, never the visible label.
Click actions receive exact raw values after parsing; external visible labels
cannot create or modify those actions. A configured JavaScript block containing
literal display data is displayed as text rather than executed with a data
fragment. When that protected block appears inside an authored click attribute,
its placeholders use the raw action bindings (or empty values when absent),
independently of the visible label. Unrelated configured scripts retain their existing handling.

Spacing comes from the trusted template. No padding or invisible separator is
added by the literal renderer. A literal section sign uses `(section)` because
Minecraft's legacy text handling interprets section-sign color sequences.
Authored `%NewLine%` separators split before restoring external values.
Trusted ordinary visible templates expand once before binding admission so introduced rich components retain separate visible and exact action lanes. Command, URL and suggestion values are shielded before display separators are revealed; a display name's multiline value never supplies the raw action value.

Trusted legacy display overrides retain their established line separators. Only
those separators are exposed before splitting; their text fragments remain
protected through provider/script evaluation. Raw command and URL values keep
their exact contents, including separator-like text.
Both existing `%SiteName%` and `{SiteName}` placeholder forms are supported,
including case variants and authored hover or click attributes. Existing display
overrides keep their interactive-syntax protection when mixed with literal ones.
Configured legacy colors in those display overrides remain available in visible
text and hover text. Trusted legacy text is converted to styled components before
literal restoration; exact command and URL attributes remain protected throughout.

Broadcast source placeholders are captured on the source owner, with an
immutable protected template passed to each recipient owner. Console delivery
uses final text without another ampersand-formatting pass. Reload/disable
checks precede scheduled delivery. The new renderer does not introduce a reward
claim path, change vote accounting, or promise atomic/exactly-once execution of
arbitrary external actions.

The reward action waits for console and recipient delivery, rather than merely
task submission. A disconnected recipient is skipped. Scheduler rejection,
rendering or transport failure, disable, and a 30-second dispatch deadline fail
the action and invalidate pending callbacks. An already delivered message cannot
be rolled back if another recipient fails; interrupted or ambiguous actions keep
the existing conservative replay reservation. Failures proven to occur before
formatting starts release that reservation. Player delivery rechecks the same
player instance and online state after formatting and before each component.
A disconnect or reconnect during formatting fails delivery instead of silently
checkpointing an unsent message. Because trusted providers or scripts may
already have run, this failure retains the existing replay identity; retrying
the same action can deliver its uncompleted message. Replay identity uses the configured
template rather than mutable checkpoint metadata or live placeholder values.

Replay-aware literal broadcasts freeze the original audience as player UUIDs and console delivery steps. The existing reward journal checkpoints each successfully completed recipient line before advancing; retry and serialized recovery skip that completed prefix, even if the current audience changes. Each action occurrence has a separate lane, so two identical configured broadcasts remain distinct. The plan allows at most 8192 delivery steps and 4096 authored lines. Recipient lookup uses the global scheduler, and rendering and delivery use the recipient owner; console rendering uses the global owner. Each outstanding delivery has a 30-second deadline. Default deadlines are cancelled when delivery succeeds, fails or is cancelled, so completed lines do not retain timer entries.

New broadcast plans retain one unsplit console payload, including the configured
`%NewLine%`/`%newline%` markers and rich `[Text=...]` syntax as text. Trusted colors
still apply; literal bindings are restored after color handling and are never
reparsed. This preserves the established console shape while players retain
per-line progress. Persisted older split-console plans keep their existing steps
and completion checkpoints; recovery does not migrate or repeat their prefix.

Delivery and journal persistence are not one atomic transaction. A crash or failed checkpoint after a transport handoff can still leave an ambiguous step; this does not provide universal exactly-once messaging. Older pending literal broadcast reservations without recipient progress remain pending rather than blindly repeating the whole broadcast. Completed older actions remain completed. Disconnected or retired original recipients are skipped, and newly connected players do not join a replayed audience. No vote accounting or additional reward path is introduced.

Replay-aware literal player messages also checkpoint each successfully delivered
line in the existing reward journal. Serialized retry resumes after that prefix,
revalidates the original player identity on its owner scheduler, and fails when
the player disconnects before an outstanding line. Older pending player-message
reservations without line progress remain pending rather than repeating an
ambiguous prefix. Completed older actions remain completed. The same 4096-line
bound applies; transport delivery and checkpoint publication are still not atomic.
Player messages retain the exact `%NewLine%` separator. Broadcasts also accept
the established `%newline%` spelling. Other casing remains available to normal
placeholder processing; separator-like text inside a literal value stays literal.

With literal metadata, `RandomMessage` records its first configured choice in
existing replay-selection metadata before formatting. Retry and restart retain
that choice, including recovery of older literal action reservations. Reordering
the list does not reroll it. Removing the chosen template leaves the queued action
pending rather than interpreting arbitrary stored text or delivering a new choice.
Restore the original choice before retrying. This opt-in lane permits up to 4096
choices, 65536 UTF-16 units per template and 1048576 combined template units.
The ordinary non-literal `RandomMessage` path keeps its existing behavior.

Collected actions are bounded to 8192 registrations. Before reserving a new
action snapshot, admission checks the existing 1 MiB replay-metadata budget for
the snapshot, current replay state and eventual completion ledger together.
Rejected admission publishes no new checkpoint and executes no collected prefix;
existing durable progress remains intact.
A collection decodes its existing reservation snapshot once for registration and
random-choice recovery; final admission separately validates current persisted
state. Large retries do not repeatedly decode the same snapshot for each action.

On Folia, broadcast recipients use the supported native
[entity scheduler retirement callback](https://jd.papermc.io/paper/1.20.6/io/papermc/paper/threadedregions/scheduler/EntityScheduler.html).
A recipient removed before dispatch settles as skipped; reward continuations run
outside the scheduler's critical retirement callback. Reflection keeps these
optional Paper API types out of Spigot class loading.

Literal values are limited to 8192 UTF-16 units; serialized UTF-8/Base64 metadata
allows the corresponding worst-case byte expansion and rechecks the decoded limit.
Their guarded legacy display
representations may use up to 32768 characters. Public preparation admits at most 8192 entries in each binding map and
1,048,576 combined key/value UTF-16 units before building marker indexes. Durable replay metadata has its own allowance of 8192 entries and 1,048,576 key/value UTF-16 units. Admitting a display does not count internal action checkpoints or saved random selections against the display allowance, and does not remove them from the durable reward owner. Both allowances are checked before copying or building indexes; excess in either lane is rejected.
Literal keys retain their 128-character limit; raw action keys are bounded to
1024 characters. Unreferenced bindings do not consume the 4096 active-binding
limit. Template and action marker matching uses indexes rather than one
full template scan per binding. Collision detection indexes the existing printable
token grammar once per input corpus; allocations use exact membership checks.
Restoration scans the parsed output once and never reinterprets inserted values.
Line and recipient variants share immutable bindings and collision metadata,
while each recipient owns any newly formatted action bindings. Retained formatted
action text and protected script text each have a 262144-character budget;
collision metadata is capped at 131072 distinct tokens across repeated transformations.
Mixed legacy/literal messages use the same indexed marker and collision authority.
Configured legacy colors and ordered ordinary-placeholder expansion are retained.
Exceptional shared-boundary or arbitrary-key expansion has a 16 MiB scan-work
budget; exceeding it fails rendering instead of monopolizing an owner thread.
Metadata, templates and component trees retain their separate bounds. Invalid literal
metadata fails rendering rather than being parsed as ordinary executable text.
Callers of the public preparation/rendering methods must use the appropriate
player owner for player-aware placeholder providers. The existing reward
pipeline remains responsible for scheduling its injectors.

Tests cover final component output, raw click actions, metadata serialization,
legacy behavior and mock scheduler ownership. This is not a claim of live
Minecraft client or Folia verification.

Replay selections and action checkpoints remain internal durable metadata, outside the display-binding index. Their encoded size does not reduce the documented random-message template limit. Ordered ordinary substitution also retains markers formed by adjacent text after an empty or delimiter-free replacement.
Trusted formatted templates may expand to the existing 262144-character result budget, while authored input remains limited to 65536 UTF-16 units. This accommodates opaque token expansion without unbounded rendered text.
Ordered expansion charges pending buffers before descending as well as completed cached text, within the same combined character budget. Ordinary bindings retain the historical fresh-map iteration order independently of unused metadata.
