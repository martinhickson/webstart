# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where practical for `-bravura` maintenance releases.

## [Unreleased]

### Added

- HTTP `Range` request support (RFC 7233) in `JnlpDownloadServlet` so JNLP clients can resume interrupted downloads
  - Single-range requests are answered with `206 Partial Content` and a `Content-Range` header
  - Unsatisfiable ranges return `416 Requested Range Not Satisfiable` with `Content-Range: bytes */<length>`
  - File-backed resources are streamed via a NIO `FileChannel` (`transferTo`); URL-backed resources fall back to a skipped stream
  - Full file responses now advertise `Accept-Ranges: bytes`
- Unit tests for the new `HttpRange` parser and Undertow integration tests covering partial, suffix, open-ended, single-byte, multi-range, unknown-unit, empty-resource, end-clamping, oversized/overflowing numbers and unsatisfiable range requests

### Fixed

- `HEAD` requests now send `200` via `setStatus` instead of `sendError`, so the reported `Content-Length` reflects the resource rather than a generated error page
- `Range` headers using an unknown range unit (e.g. `items=0-9`) are ignored per RFC 7233 section 2.3 instead of answered with `416`
- `If-Modified-Since` is ignored when a `Range` header is present (RFC 7232 section 3.3), so a client resuming a download receives `206 Partial Content` instead of `304 Not Modified`
- Empty suffix specs (`bytes=-`) are rejected, and overflowing range numbers no longer crash parsing: oversized ends/suffixes clamp to the representation while oversized starts are unsatisfiable (`416`)
- `HEAD` responses and gzip/pack200 variants report `Content-Length` as a `long`, avoiding int overflow for resources larger than 2 GiB
- Resource paths are URL-decoded before lookup (was: only the encoded `getRequestURI` was used), so files with spaces or encoded characters are now served
- `If-Range` support (RFC 7233 section 3.2): a date-based `If-Range` that no longer matches the representation causes the `Range` header to be ignored and the full content to be served; malformed `If-Range` values are ignored
- Extended integration tests to cover pack200-gzip variants, version-based lookups (`name__V<version>.jar`), mixed-unit ranges, `If-Range` match/mismatch/malformed, blocked direct access to versioned files, unicode-encoded paths, and empty-resource ranges

## [1.2.4-bravura] - 2026-07-18

### Added

- GitHub Actions CI on push and pull requests (unit tests, JaCoCo, servlet ITs, and `webstart-it`)
- `CONTRIBUTING.md`, Dependabot for GitHub Actions, and JaCoCo artifacts in CI
- Expanded unit tests for config beans, dependency tasks, generator configs, and servlet `VersionID`/`VersionString`
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
