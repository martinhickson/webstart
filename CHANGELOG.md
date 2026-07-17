# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where practical for `-bravura` maintenance releases.

## [1.2.4-bravura] - 2026-07-18

### Added

- GitHub Actions CI on push and pull requests (unit tests, JaCoCo, servlet ITs, and `webstart-it`)
- JaCoCo code coverage reporting and minimum thresholds on `webstart-maven-plugin`
- 77+ unit tests covering Pack200, dependency handling, utilities, and build reporting
- `webstart-jnlp-servlet-it` module with Undertow-based Failsafe integration tests
- `integration-test` Maven profile to build servlet ITs on demand

### Changed

- `webstart-jnlp-servlet` migrated from `javax.servlet` to Jakarta Servlet 6 (Jakarta EE 10)
- Maven coordinates published under `io.github.martinhickson` on Maven Central and GitHub Packages
- README updated for Central-first installation, JaCoCo, servlet ITs, and CI

### Fixed

- Pack200 graceful degradation on JDK 21 when the platform Pack200 API is unavailable
- JNLP servlet temp directory lookup uses `jakarta.servlet.context.tempdir`

## [1.2.1-bravura] - 2026-07-17

### Added

- Maven Central publishing for plugin and servlet artifacts
- Expanded unit test coverage for core plugin utilities

### Changed

- README and examples aligned to Maven Central coordinates

## [1.0.7-bravura] - 2026-07-16

### Added

- Manual GitHub Actions release workflow with optional Maven Central deploy
- `webstart-it` standalone integration project for JDK 21 plugin verification
- Pack200 fallback via Apache Commons Compress when JDK Pack200 is unavailable

### Changed

- JDK 11+ compilation with JDK 21 build and runtime support
- Azure Key Vault JCA and hardware key signing improvements

[1.2.4-bravura]: https://github.com/martinhickson/webstart/releases/tag/v1.2.4-bravura-release
[1.2.1-bravura]: https://github.com/martinhickson/webstart/releases/tag/v1.2.1-bravura-release
[1.0.7-bravura]: https://github.com/martinhickson/webstart/releases/tag/v1.0.7-bravura-release
