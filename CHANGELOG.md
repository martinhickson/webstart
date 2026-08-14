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
- `HEAD` responses now advertise `Accept-Ranges: bytes` like full and partial responses
- `HEAD` responses now mirror the `GET` response for the negotiated encoding (RFC 9110 section 9.3.2): the head branch resolves the gzip/pack200-gzip variant and sets `Content-Encoding` plus the variant's `Content-Length`, instead of always reporting the plain resource
- URI paths are percent-decoded with `+` preserved as a literal character (RFC 3986 section 3.3); previously `URLDecoder` turned `+` into a space, so files with `+` in their name were not found
- `JarDiffHandler.isJavawsVersion` and `JarDiffKey` are null-safe: a `current-version-id` request without a `User-Agent` header, or on a basic (non-versioned) resource, no longer throws and falls back to serving the file
- `ResourceCatalog` now uses a `ConcurrentHashMap` with `compute()` instead of a plain `HashMap` behind a whole-method lock: cache reads are lock-free and a re-scan is atomic only for the affected directory, so versioned lookups of different directories no longer serialize
- Integration tests now cover nested-directory resources and versioned lookups, versioned pack200 variants (range, unsatisfiable, HEAD), `If-Modified-Since` on versioned resources, header edges (no `Accept-Ranges` on 304, `Last-Modified` on full downloads, HEAD on empty resources), text files, 40 sequential ranges, and mixed concurrent range/full/HEAD requests
- `HEAD` requests now honour `If-Modified-Since` (RFC 9110 section 9.3.2): a matching conditional returns `304` instead of always `200`
- `Accept-Encoding` negotiation now honours q-values (RFC 7231 section 5.3.4): `gzip;q=0` or `pack200-gzip;q=0` no longer select the compressed variant, `pack200-gzip` no longer falls back to plain gzip, and `identity`/`*` do not enable compression
- Added q-value, direct `.gz`/`.pack.gz` range, nested-path HEAD, empty-query, URL-fallback concurrency, sequential-suffix, text-file suffix, future-`If-Modified-Since`-on-HEAD, and content-type partial tests
- A malformed `If-Modified-Since` date is now ignored instead of failing the request with `500` (RFC 7232 section 3.3)
- Added tests for greater-than (`1.0+`) and wildcard (`1.*`) version constraints, empty `Range` headers, unit-only `Range` values, direct gzip access with q-zero encoding, mixed concurrent encoding requests, sequential open-ended ranges, and query-bearing JNLP requests
- Integration tests now cover the `version.xml` platform-matching path (`platform-version-id`) end to end: range, full, suffix, unsatisfiable and HEAD responses on a platform-resolved resource, plus direct `version.xml` access being blocked
- Added malformed `If-Range` and `If-Range`/`If-Modified-Since` on the URL-fallback path, `Accept-Encoding` q-values with spaces/extra params, tiny-file ranges, and a concurrent versioned+conditional request mix
- The `version.xml` `<resource>` entry path is now covered end to end: virtual resources (`other.jar`, os-constrained `osjar.jar`) resolved purely through `version.xml`, with range, unsatisfiable, HEAD, os-match and os-no-match tests
- Added hash-in-filename (`%23`), NUL-byte path rejection, version lookup on the URL-fallback server, platform no-match error, HEAD on a tiny file, and empty `Accept-Encoding` tests
- Conditional validators now apply to the selected representation (RFC 7232 section 2.2): `If-Modified-Since` and `If-Range` are evaluated against the gzip/pack200 variant actually served when `Accept-Encoding` negotiates one, instead of the plain resource's `Last-Modified`
- Added conditional-on-encoded-variant tests (gzip/pack200/versioned-gzip) with controlled fixture timestamps so the previously wrong behaviour would fail deterministically
- `Accept-Encoding` parameter names are matched case-insensitively (RFC 7230 section 3.2.6): `gzip;Q=0` is now rejected
- Added HEAD-with-encoding conditional, `Last-Modified`-on-gzip-partial, `;jsessionid` path rejection, versioned pack200 conditional and suffix, and concurrent `platform-version-id` download tests
- Added locale-constrained versioned resources, case-sensitive and double-encoded path tests, `If-Range` on versioned resources, emoji filenames, versioned single-byte ranges, HEAD on versioned resources, and cross-directory concurrent versioned downloads
- The `webstart-jnlp-servlet-it` binary fixtures are generated at test runtime instead of being committed; only the text `launch.jnlp` fixture is tracked
- The streamed (non-NIO) range fallback for resources inside packaged JARs is now covered by integration tests via a second Undertow deployment
- Integration tests now cover path traversal rejection (raw and percent-encoded, with and without `Range`), entity-tag-form `If-Range`, case-insensitive `Accept-Encoding` values, future/old `If-Modified-Since`, os/arch-constrained versioned resources, `platform-version-id` error responses, blocked `version.xml`, trailing-slash paths, and `Range` headers lacking `=`
- Integration tests now serve from a filesystem-backed web root, enabling multi-GiB (sparse) resource coverage: `Content-Length` above 2 GiB, NIO transfers at offsets beyond the int range, and stale-`If-Range` behaviour after a resource is replaced on disk
- Added concurrent partial-download, repeated-`Range`-header, missing-resource, gzip/pack200 unsatisfiable and suffix ranges, zero-byte resource, `Accept-Ranges` on `416`, and full-download-after-partial tests
- Added HEAD-on-gzip/pack200 parity tests, 1 MiB partial transfers on the multi-GiB resource, future-dated `If-Range`, gzip encoding on files without a `.gz` variant, pack200-gzip on JNLP files, and multi-GiB suffix/open-ended/end-clamped range tests
- Added URL-fallback range tests, paths containing literal/encoded `+`, versioned gzip variants, pack200-over-gzip encoding preference, jardiff fallback with and without `User-Agent`, zero-suffix rejection, pack200 unsatisfiable ranges, and full ranges on versioned resources
- Added encoding q-value (`gzip;q=0.5`, `pack200-gzip;q=1.0`) and `identity`/`*` negotiation tests, jardiff generation failure fallback, version-not-found errors, >4 GiB offsets on a 5 GiB sparse file, URL-fallback HEAD/suffix ranges, concurrent versioned downloads, multi-range unsat, version-id+current-version-id combos, and a parser invariant sweep

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
