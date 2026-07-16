# webstart-it

Standalone integration project for the **webstart-maven-plugin** on modern JDKs (including JDK 21).

This module is **not** part of the parent Maven reactor. It is built separately after installing the plugin from the repository root.

## Prerequisites

Install the plugin locally from the repository root:

```bash
cd ..
mvn clean install -DskipTests
```

## Run on JDK 21

```bash
mvn clean verify -f webstart-it/pom.xml
```

Override the plugin version if needed:

```bash
mvn clean verify -f webstart-it/pom.xml -Dwebstart.plugin.version=1.0.1-bravura-SNAPSHOT
```

## What it verifies

- JNLP inline generation with signing on a modern JDK
- Pack200 configured but gracefully skipped when the JDK Pack200 API is unavailable (with a Maven warning)
- End-to-end plugin execution independent of the invoker IT suite

Expected output includes `target/jnlp/launch.jnlp` and signed JAR artifacts under `target/jnlp/`.
