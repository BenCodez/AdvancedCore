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
processing. Visible text and hover text are restored only into final components.
Click actions receive exact raw values after parsing; external visible labels
cannot create or modify those actions. A configured JavaScript block containing
literal display data is displayed as text rather than executed with a data
fragment. Unrelated configured scripts retain their existing handling.

Spacing comes from the trusted template. No padding or invisible separator is
added by the literal renderer. A literal section sign uses `(section)` because
Minecraft's legacy text handling interprets section-sign color sequences.
Authored `%NewLine%` separators split before restoring external values.
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
formatting starts release that reservation. Replay identity uses the configured
template rather than mutable checkpoint metadata or live placeholder values.

On Folia, broadcast recipients use the supported native
[entity scheduler retirement callback](https://jd.papermc.io/paper/1.20.6/io/papermc/paper/threadedregions/scheduler/EntityScheduler.html).
A recipient removed before dispatch settles as skipped; reward continuations run
outside the scheduler's critical retirement callback. Reflection keeps these
optional Paper API types out of Spigot class loading.

Bindings, templates and component trees have bounded sizes. Invalid literal
metadata fails rendering rather than being parsed as ordinary executable text.
Callers of the public preparation/rendering methods must use the appropriate
player owner for player-aware placeholder providers. The existing reward
pipeline remains responsible for scheduling its injectors.

Tests cover final component output, raw click actions, metadata serialization,
legacy behavior and mock scheduler ownership. This is not a claim of live
Minecraft client or Folia verification.
