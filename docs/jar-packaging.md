# JAR packaging contract

AdvancedCore consumes SimpleAPI's `thin` classifier so Maven Shade sees the
project classes and each dependency exactly once. The normal SimpleAPI artifact
remains a self-contained compatibility artifact for other consumers.

AdvancedCore does not use SimpleAPI's HTTP, Redis, or MQTT implementations.
Its SimpleAPI dependency therefore excludes Bouncy Castle, Jedis, and Paho.
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
