# JAR packaging contract

AdvancedCore consumes SimpleAPI's normal self-contained artifact for SNAPSHOT
compatibility, then applies an artifact-specific Shade filter for features it
does not use. This also removes content already embedded upstream, which Maven
dependency exclusions alone cannot affect.

AdvancedCore does not use SimpleAPI's HTTP, Redis, or MQTT implementations.
Its SimpleAPI dependency therefore excludes Bouncy Castle, Jedis, and Paho.
The dependency is published as optional because the required SimpleAPI classes
are embedded in AdvancedCore's JAR; this prevents downstream shading builds from
pulling the original unfiltered SimpleAPI JAR back into their distributions.
Hikari, FoliaLib, Configurate, and UniDialog remain available because the
AdvancedCore API and implementation use them. Hikari and FoliaLib retain their
existing `com.bencodez.simpleapi.*` relocated package names.

Gson is declared as `provided`: AdvancedCore compiles against API signatures
that contain Gson types, while the supported server/proxy platforms supply it.
Rhino remains bundled and relocated for the JavaScript feature.

The package phase runs `PackagedArtifactTest` after shading. It verifies the
actual minimized JAR contains required relocated classes and no HTTP crypto,
Redis, MQTT, or Java 25 Bouncy Castle payload. Deployment profiles use the same
Shade configuration; only publication/final-name behavior differs.
