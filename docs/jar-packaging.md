# JAR packaging contract

AdvancedCore consumes SimpleAPI's normal self-contained artifact for SNAPSHOT
compatibility and preserves the established transitive dependency contract.
It does not add an AdvancedCore-specific payload filter or change dependency
publication semantics for existing consumers.

AdvancedCore does not use SimpleAPI's HTTP, Redis, or MQTT implementations.
Its SimpleAPI dependency therefore excludes Bouncy Castle, Jedis, and Paho.
Hikari, FoliaLib, Configurate, and UniDialog remain available because the
AdvancedCore API and implementation use them. Hikari and FoliaLib retain their
existing `com.bencodez.simpleapi.*` relocated package names.

Gson is declared as `provided`: AdvancedCore compiles against API signatures
that contain Gson types, while the supported server/proxy platforms supply it.
Rhino remains bundled and relocated for the JavaScript feature.

The package phase runs `PackagedArtifactTest` after shading. It verifies the
actual minimized JAR contains its required classes and relocations. VotingPlugin
applies its own narrow artifact filters when producing its smaller user-facing
distribution.
Deployment profiles use the same Shade configuration; only
publication/final-name behavior differs.
