# BURP Developer & Release Guide

This document covers the java part, for VS Code extension specific developer instructions see
the [rml-dev-server/dev.md](rml-dev-server/dev.md).

## Prerequisites

- **Java JDK 21+** (Eclipse Temurin recommended, e.g., `/usr/lib/jvm/java-21-temurin-jdk`)
- **Apache Maven 3.9+** (or 3.10+)
- **Node.js 20+** and **npm** (for the extension in `rml-dev-server/`)
- **Docker** (for RML-IO-Registry database tests with Testcontainers)

## Building BURP

### Standard Build (Skip Tests)

```bash
mvn clean package -DskipTests
```

### Standalone Fat JAR (`target/burp.jar`)

The `rml-dev-server` extension, benchmark modules, and CLI release bundles expect the unversioned shaded JAR located at
`target/burp.jar`. Always build using the `deploy` Maven profile:

```bash
mvn package -Pdeploy -DskipTests
```

## Running Tests

### Standard Core & RML Tests

```bash
mvn test -Dtest="burp.**,rml.**"
```

### TurtleProv Manifest Tests

The custom Turtle provenance parser (`turtleprov`) contains experimental conformance test suites:

```bash
mvn test -Dtest="turtleprov.**"
```

### Performance Benchmarks (`perf` Profile)

Performance and comparison suites (e.g., incremental evaluation, CARML comparisons) are extracted into the `benchmarks/`
submodule and excluded from standard builds by default:

```bash
mvn test -Pperf
```

Or targeting specific benchmark tests:

```bash
mvn test -Pperf -pl benchmarks -Dtest="IncrementalPerformanceTest"
```

---

## 4. Maven Central Deployment Setup

BURP publishes artifacts to the Sonatype Central Portal (`https://central.sonatype.com`).

### Configuring `~/.m2/settings.xml`

Maven requires server authentication credentials configured in your user settings file (`~/.m2/settings.xml`).
The `<server>` ID must be `central`:

```xml

<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
    <servers>
        <server>
            <id>central</id>
            <username>YOUR_CENTRAL_PORTAL_USER_TOKEN</username>
            <password>YOUR_CENTRAL_PORTAL_TOKEN_PASSWORD</password>
        </server>
    </servers>
</settings>
```

### Local Deploy Dry-Run (Without Uploading)

To verify signing, bundle structure, and POM validation locally without uploading to Maven Central:

```bash
mvn clean deploy -Pdeploy -DskipTests -Dgpg.skip=true -Dsigstore.skip=true -DskipPublishing=true
```

## Release Workflow & GitHub CI

BURP releases are automated via GitHub Actions in [.github/workflows/deploy.yml](.github/workflows/deploy.yml).

### Triggering a Release

Releases are triggered by pushing a version tag prefixed with `v`:

```bash
# For a release candidate
git tag v0.1.4-rc.3
git push origin v0.1.4-rc.3

# For a stable release
git tag v0.1.4
git push origin v0.1.4
```
