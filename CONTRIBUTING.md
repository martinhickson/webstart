# Contributing

Thank you for contributing to the WebStart Maven plugin.

## Prerequisites

- JDK 11 or later to compile (JDK 21 recommended for build and runtime)
- Maven 3.6+

## Build and test

From the repository root:

```bash
mvn clean install
```

Run servlet integration tests:

```bash
mvn verify -Pintegration-test
```

Run the standalone end-to-end plugin harness:

```bash
mvn verify -f webstart-it/pom.xml
```

JaCoCo reports are written to:

- `webstart-maven-plugin/target/site/jacoco/`
- `target/site/jacoco-aggregate/`

## Pull requests

1. Fork the repository and create a feature branch from `main`.
2. Keep changes focused; match existing code style and naming.
3. Add or update unit tests for behavior you change.
4. Ensure `mvn verify -Pintegration-test` passes locally.
5. Open a pull request against `main`. CI runs the same checks automatically.

## Releases

Maintainers cut releases manually via **Actions → Release → Run workflow**. See [README.md](README.md#releasing) for workflow inputs and Maven Central publishing.

## Questions

Open a [GitHub issue](https://github.com/martinhickson/webstart/issues) for bugs, feature requests, or maintenance questions.
