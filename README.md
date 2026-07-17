# A WebStart Plugin for Maven

[![CI](https://github.com/martinhickson/webstart/actions/workflows/ci.yml/badge.svg)](https://github.com/martinhickson/webstart/actions/workflows/ci.yml)
[![Integration Test](https://github.com/martinhickson/webstart/actions/workflows/integration-test.yml/badge.svg)](https://github.com/martinhickson/webstart/actions/workflows/integration-test.yml)
[![Release](https://github.com/martinhickson/webstart/actions/workflows/release.yml/badge.svg)](https://github.com/martinhickson/webstart/actions/workflows/release.yml)
[![MIT Licence](https://img.shields.io/github/license/martinhickson/webstart.svg?label=License)](http://opensource.org/licenses/MIT)

Build, sign, and package **Java Web Start** applications with Maven. This plugin generates JNLP descriptors, bundles dependencies, signs JARs, and optionally applies Pack200 compression — ready for deployment with [IcedTea-Web](https://github.com/martinhickson/IcedTea-Web) or any JNLP-aware client runtime.

Actively maintained for modern JDKs, cloud code signing, and current enterprise use.

See [CHANGELOG.md](CHANGELOG.md) for release history and [CONTRIBUTING.md](CONTRIBUTING.md) for development setup.

**Latest release:** [`1.2.4-bravura`](https://github.com/martinhickson/webstart/releases/tag/v1.2.4-bravura-release) · **Default branch:** `main` · **Maven Central:** `io.github.martinhickson:webstart-maven-plugin:1.2.4-bravura`

---

## What it does

| Capability | Description |
|------------|-------------|
| **JNLP generation** | Produce `launch.jnlp` (and related files) from your project and dependencies |
| **JAR signing** | Sign application and dependency JARs; supports hardware keys and Azure KeyVault JCA |
| **Pack200** | Optional compression for legacy Web Start deployments; graceful fallback on modern JDKs |
| **Servlet support** | `webstart-jnlp-servlet` module (Jakarta Servlet 6 / EE 10) for Pack200-aware JNLP download serving |

---

## Quick start

Add the plugin to your `pom.xml` and bind the `jnlp-inline` goal (simplest path for a single JAR application):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.martinhickson</groupId>
      <artifactId>webstart-maven-plugin</artifactId>
      <version>1.2.4-bravura</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals>
            <goal>jnlp-inline</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <jnlp>
          <outputFile>launch.jnlp</outputFile>
          <mainClass>com.example.MyApplication</mainClass>
        </jnlp>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Build:

```bash
mvn clean package
```

Output lands under `target/jnlp/` — including `launch.jnlp`, signed JARs, and a distributable ZIP.

Released versions are published to **[Maven Central](https://central.sonatype.com/artifact/io.github.martinhickson/webstart-maven-plugin)** — no extra `<repository>` configuration is required.

### Installing from GitHub Packages

GitHub Packages is also available if you prefer to resolve from GitHub. Add the repository and authenticate with a GitHub personal access token (`read:packages`):

```xml
<repositories>
  <repository>
    <id>github-packages</id>
    <url>https://maven.pkg.github.com/martinhickson/webstart</url>
  </repository>
</repositories>
```

In `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github-packages</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
  </server>
</servers>
```

---

## Usage examples

### Signed application (development keystore)

For local builds, the plugin can generate a keystore automatically:

```xml
<configuration>
  <jnlp>
    <outputFile>launch.jnlp</outputFile>
    <mainClass>com.example.MyApplication</mainClass>
  </jnlp>
  <sign>
    <keystore>${project.build.directory}/keystore</keystore>
    <storepass>changeit</storepass>
    <keypass>changeit</keypass>
    <alias>myapp</alias>
    <keyalg>RSA</keyalg>
    <validity>365</validity>
    <dnameCn>example.com</dnameCn>
    <dnameO>Example Ltd</dnameO>
    <dnameC>US</dnameC>
    <keystoreConfig>
      <gen>true</gen>
      <delete>true</delete>
    </keystoreConfig>
  </sign>
</configuration>
```

On JDK 11 and later, specify `keyalg` (for example `RSA`) when generating keys.

### Pack200 on a modern JDK

Pack200 is still relevant for some legacy client runtimes. On JDK 14+, the JDK Pack200 API is unavailable. You have two options:

**Option A — skip Pack200** (default behaviour when unavailable): enable Pack200 and the plugin logs a warning, then continues:

```xml
<pack200>
  <enabled>true</enabled>
</pack200>
```

**Option B — use Apache Commons Compress** on modern JDKs:

```xml
<pack200>
  <enabled>true</enabled>
  <commonsCompressEnabled>true</commonsCompressEnabled>
</pack200>
```

### Separate JNLP file per module (`jnlp` goal)

For multi-module layouts or WAR packaging, use the `jnlp` goal instead of `jnlp-inline`. See [webstart-it/](webstart-it/) for a working JDK 21 example.

---

## Azure Key Vault cloud signing

Sign JARs during the JNLP build using a certificate stored in **Azure Key Vault**, via the [Azure Key Vault JCA provider](https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/keyvault/azure-security-keyvault-jca). The plugin passes `providerName`, `providerClass`, `certchain`, and `-J` JVM arguments through to `jarsigner` — including any arguments you declare in the POM (they are preserved across signing).

### Prerequisites

1. **Azure Key Vault** with a code-signing certificate imported (certificate name = jarsigner alias).
2. **Key Vault permissions** — RBAC: `Key Vault Crypto User`, `Key Vault Certificate User`, and `Key Vault Secrets User`; or access policy with `Sign` on keys and `get`/`list` on certificates and secrets.
3. **Authentication** — service principal (tenant/client/secret), managed identity, or workload identity on your build agent.
4. **Certificate chain PEM** — export the full chain from your CA and save as `src/main/signing/cert-chain.pem`. Supplying `-certchain` avoids incomplete-chain warnings from the JCA provider when the vault holds only the leaf certificate.
5. **JDK 11+** on the build machine (module-path to the JCA JAR is required on Java 9+).

### Full `pom.xml` example

Service-principal authentication with secrets supplied from the environment (recommended for CI):

```xml
<project>
  <!-- ... modelVersion, groupId, artifactId, version ... -->

  <properties>
    <azure.keyvault.uri>https://my-company.vault.azure.net/</azure.keyvault.uri>
    <azure.keyvault.cert-name>my-code-signing-cert</azure.keyvault.cert-name>
    <azure.jca.version>2.11.0</azure.jca.version>
    <azure.jca.jar.path>${project.build.directory}/jca/azure-security-keyvault-jca-${azure.jca.version}.jar</azure.jca.jar.path>
    <webstart.plugin.version>1.2.4-bravura</webstart.plugin.version>
  </properties>

  <dependencies>
    <!-- Used only to resolve and copy the JCA provider JAR onto the module-path -->
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-security-keyvault-jca</artifactId>
      <version>${azure.jca.version}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- Copy azure-security-keyvault-jca.jar before signing -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <executions>
          <execution>
            <id>copy-azure-jca</id>
            <phase>generate-resources</phase>
            <goals>
              <goal>copy</goal>
            </goals>
            <configuration>
              <artifactItems>
                <artifactItem>
                  <groupId>com.azure</groupId>
                  <artifactId>azure-security-keyvault-jca</artifactId>
                  <version>${azure.jca.version}</version>
                  <outputDirectory>${project.build.directory}/jca</outputDirectory>
                </artifactItem>
              </artifactItems>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>io.github.martinhickson</groupId>
        <artifactId>webstart-maven-plugin</artifactId>
        <version>${webstart.plugin.version}</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>jnlp-inline</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <jnlp>
            <outputFile>launch.jnlp</outputFile>
            <mainClass>com.example.MyApplication</mainClass>
          </jnlp>

          <sign>
            <!-- Azure Key Vault keystore -->
            <keystore>NONE</keystore>
            <storetype>AzureKeyVault</storetype>
            <storepass></storepass>
            <alias>${azure.keyvault.cert-name}</alias>

            <!-- JCA provider (maps to jarsigner -providerName / -providerClass) -->
            <providerName>AzureKeyVault</providerName>
            <providerClass>com.azure.security.keyvault.jca.KeyVaultJcaProvider</providerClass>

            <!-- Full PEM chain from your CA (maps to jarsigner -certchain) -->
            <certchain>${project.basedir}/src/main/signing/cert-chain.pem</certchain>

            <!-- Timestamp and digest algorithms -->
            <tsaLocation>http://timestamp.digicert.com</tsaLocation>
            <digestalg>SHA-256</digestalg>
            <verify>true</verify>

            <!-- JVM arguments forwarded to the jarsigner subprocess (-J prefix) -->
            <arguments>
              <argument>-J--module-path=${azure.jca.jar.path}</argument>
              <argument>-J--add-modules=com.azure.security.keyvault.jca</argument>
              <argument>-J-Dazure.keyvault.uri=${azure.keyvault.uri}</argument>
              <argument>-J-Dazure.keyvault.tenant-id=${env.AZURE_TENANT_ID}</argument>
              <argument>-J-Dazure.keyvault.client-id=${env.AZURE_CLIENT_ID}</argument>
              <argument>-J-Dazure.keyvault.client-secret=${env.AZURE_CLIENT_SECRET}</argument>
            </arguments>
          </sign>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### Build with service principal credentials

Set credentials in the environment, then package as usual:

```bash
export AZURE_TENANT_ID="00000000-0000-0000-0000-000000000000"
export AZURE_CLIENT_ID="00000000-0000-0000-0000-000000000000"
export AZURE_CLIENT_SECRET="your-client-secret"

mvn clean package
```

Signed JARs and `launch.jnlp` are written under `target/jnlp/`.

### Managed identity (Azure VM, App Service, GitHub Actions OIDC, etc.)

When the build agent already has Azure credentials (managed identity or `az login`), omit tenant/client/secret and pass only the vault URI:

```xml
<sign>
  <keystore>NONE</keystore>
  <storetype>AzureKeyVault</storetype>
  <storepass></storepass>
  <alias>${azure.keyvault.cert-name}</alias>
  <providerName>AzureKeyVault</providerName>
  <providerClass>com.azure.security.keyvault.jca.KeyVaultJcaProvider</providerClass>
  <certchain>${project.basedir}/src/main/signing/cert-chain.pem</certchain>
  <tsaLocation>http://timestamp.digicert.com</tsaLocation>
  <digestalg>SHA-256</digestalg>
  <verify>true</verify>
  <arguments>
    <argument>-J--module-path=${azure.jca.jar.path}</argument>
    <argument>-J--add-modules=com.azure.security.keyvault.jca</argument>
    <argument>-J-Dazure.keyvault.uri=${azure.keyvault.uri}</argument>
  </arguments>
</sign>
```

For a **user-assigned** managed identity, add:

```xml
<argument>-J-Dazure.keyvault.managed-identity=${env.AZURE_MANAGED_IDENTITY_CLIENT_ID}</argument>
```

### Equivalent `jarsigner` command

The configuration above corresponds to:

```bash
jarsigner \
  -keystore NONE \
  -storetype AzureKeyVault \
  -storepass "" \
  -signedjar signed.jar unsigned.jar "${AZURE_KEYVAULT_CERT_NAME}" \
  -certchain src/main/signing/cert-chain.pem \
  -providerName AzureKeyVault \
  -providerClass com.azure.security.keyvault.jca.KeyVaultJcaProvider \
  -digestalg SHA-256 \
  -tsa http://timestamp.digicert.com \
  -J--module-path=target/jca/azure-security-keyvault-jca-2.11.0.jar \
  -J--add-modules=com.azure.security.keyvault.jca \
  -J-Dazure.keyvault.uri=https://my-company.vault.azure.net/ \
  -J-Dazure.keyvault.tenant-id="${AZURE_TENANT_ID}" \
  -J-Dazure.keyvault.client-id="${AZURE_CLIENT_ID}" \
  -J-Dazure.keyvault.client-secret="${AZURE_CLIENT_SECRET}"
```

### Troubleshooting

| Symptom | Likely fix |
|---------|------------|
| Certificate chain warnings from `jarsigner` | Provide `<certchain>` with a PEM file containing the full chain from your CA |
| `Could not obtain key store location` | Use `<keystore>NONE</keystore>` for Azure Key Vault (not a file path) |
| Provider / module errors on JDK 11+ | Ensure `-J--module-path` and `-J--add-modules=com.azure.security.keyvault.jca` point at the copied JCA JAR |
| Custom `-J` arguments ignored | Upgrade to `1.2.4-bravura` or later — POM-configured arguments are preserved through signing |
| Auth failures in CI | Confirm Key Vault RBAC/access policy for the service principal or managed identity used by the agent |

---

## Plugin goals

| Goal | Purpose |
|------|---------|
| `jnlp-inline` | Generate JNLP and bundle JARs inline (most common) |
| `jnlp` | Generate JNLP for standard project layouts |
| `jnlp-single` | Single-artifact JNLP generation |
| `jnlp-download-servlet` | Generate servlet configuration for JNLP download |
| `unsign` | Remove signatures from JARs |
| `report` | Maven site report |

---

## Client runtime

Deploy JNLP applications with [IcedTea-Web](https://github.com/martinhickson/IcedTea-Web) — a maintained JNLP client with Pack200 support and modern JDK compatibility.

---

## Project layout

```
webstart/
├── webstart-maven-plugin/       # The Maven plugin
├── webstart-jnlp-servlet/       # JNLP download servlet (Jakarta EE 10)
├── webstart-jnlp-servlet-it/    # Servlet ITs (Undertow; profile integration-test)
└── webstart-it/                 # Standalone plugin IT (not in reactor)
```

---

## Building from source

Requirements: **JDK 11+** to compile; **JDK 21** supported for build and runtime.

```bash
git clone https://github.com/martinhickson/webstart.git
cd webstart
git checkout main

mvn clean install
```

### Unit tests and code coverage

```bash
mvn verify -pl webstart-maven-plugin
```

JaCoCo reports are generated under `webstart-maven-plugin/target/site/jacoco/` (module) and `target/site/jacoco-aggregate/` (reactor root). Coverage thresholds apply to core library code (Mojo entry points are excluded and exercised via `webstart-it`).

Push and pull request builds run unit tests, JaCoCo checks, servlet ITs, and the `webstart-it` end-to-end harness via GitHub Actions (see `.github/workflows/ci.yml`).

### Servlet integration tests (Undertow)

The `webstart-jnlp-servlet-it` module deploys `JnlpDownloadServlet` on Undertow with Jakarta Servlet 6. It is only built when the `integration-test` profile is active:

```bash
mvn verify -Pintegration-test
```

### Plugin integration test (JDK 21)

```bash
mvn clean verify -f webstart-it/pom.xml
```

See [webstart-it/README.md](webstart-it/README.md) for details.

---

## Releasing

Releases are triggered manually from **Actions → Release → Run workflow**.

| Input | Example | Description |
|-------|---------|-------------|
| `release_version` | `1.2.4-bravura` | Version applied to all POMs |
| `skipTests` | `false` | Skip unit tests during build and deploy |
| `run_integration_tests` | `true` | Run `webstart-it` before tagging |
| `deploy_to_maven_central` | `false` | Also publish to Maven Central (optional) |

The workflow creates branch `v{version}`, tag `v{version}-release`, publishes to GitHub Packages, optionally to Maven Central, and opens a GitHub Release.

---

## Maintenance highlights

- JDK 11+ compilation and JDK 21 build/runtime support
- Pack200 graceful degradation and Commons Compress path
- Azure KeyVault JCA and hardware key signing improvements
- Maven Central publishing under `io.github.martinhickson`
- `webstart-jnlp-servlet` migrated to Jakarta Servlet 6 (Jakarta EE 10)
- Unit tests (100+) with JaCoCo coverage thresholds; servlet ITs via Undertow (`-Pintegration-test`)
- CI on push/PR; manual release workflow and standalone `webstart-it` plugin harness

---

## Licence

MIT — see [LICENSE.txt](LICENSE.txt).
