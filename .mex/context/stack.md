---
name: stack
description: Current AdvancedCore build/platform boundary.
triggers: [Java, Maven, Bukkit, proxy]
last_updated: 2026-09-20
---

# Build/runtime boundary

This checkout compiles for Java 21 from `AdvancedCore/pom.xml`; CI runs its Maven package goal. The POM includes Bukkit/Spigot, BungeeCord, and Velocity APIs. Dependency and version details should be read from the current POM, not frozen in MEX. MEX 0.8.2 indexes no Java symbols here. Source: `AdvancedCore/pom.xml`, `.github/workflows/maven.yml`.
