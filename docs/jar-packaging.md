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

## Additional safe-to-test size filtering

The normal AdvancedCore artifact embeds SimpleAPI's full published
implementation and must retain its legacy Redis classes, HTTP TLS classes,
and MQTT classes for downstream consumers, even though AdvancedCore itself does
not call those transports. Do not strip them solely on local source reachability.

The shaded JAR is not a multi-release JAR or JPMS module, so the Maven Shade
filters omit `META-INF/versions/**` and root `module-info.class`. Redundant
dependency `META-INF/maven/**/pom.xml` and `pom.properties` descriptions
are also omitted; service files, version fallbacks, licenses, native resources,
and deployed plugin metadata remain.

Checker Framework and JSpecify annotation implementation packages are filtered
from the shaded JAR, rather than changing the Java dependency graph or published
method signatures. If an external consumer compiles directly against those
annotation types, it must declare the separate annotation dependency; that
source-compatibility edge remains a review consideration.

Use `mvn -B -f AdvancedCore/pom.xml clean package` and check both
`PackagedArtifactTest` and VotingPlugin's packaged producer-compatibility test
before accepting an artifact change. The suite exercises class loading but does
not substitute for live Bukkit/Paper/Folia startup validation.
