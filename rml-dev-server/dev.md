# RML Dev Server - Developer Guide

This document describes how to set up, build, test, and package the RML Dev Server VS Code extension.

---

## Prerequisites

- **Node.js 20+** and **npm**
- **Java 21+** and **Maven** (for building BURP)

---

## Getting Started

### 1. Install Dependencies & Build Webview / Extension

From the `rml-dev-server/` directory:

```bash
npm install
npm run compile
```

This compiles:

- Bundled extension host to `dist/extension.js` and React webview to `dist/webview.js` and `dist/webview.css` using
  `esbuild`
- TypeScript compilation to `out/` (for type checking and tests)

### 2. Run in Extension Development Host

1. Open the repository root (or the `rml-dev-server/` folder) in VS Code.
2. Press `F5` (or select **Run > Start Debugging**) to launch an Extension Development Host window.
3. Open any `.ttl` or `.rml` mapping file to trigger extension activation.

Alternatively, launch from the command line:

```bash
code --extensionDevelopmentPath=$(pwd)/rml-dev-server ./rml-modules/
```

---

## Development Modes

### Running with a Local BURP Build

If you build BURP locally:

```bash
mvn package -Pdeploy -DskipTests
```

The extension automatically detects `target/burp.jar` in your workspace without downloading anything.

### Running against an External Dev Server

To debug BURP directly from IntelliJ / CLI:

1. Start BURP Dev Server:
   ```bash
   burp -d=8080
   ```
2. In VS Code settings (`.vscode/settings.json`), configure:
   ```json
   {
     "rml.server.spawnProcess": false,
     "rml.server.port": 8080
   }
   ```

The extension will connect directly to port 8080 without spawning a local process.

---

## Packaging the Extension

To produce a single universal `.vsix` package:

```bash
npm run package
```

To produce platform-specific `.vsix` packages for all desktop platforms (excluding web):

```bash
npm run package:all
```

Target platforms generated: `win32-x64`, `win32-arm64`, `linux-x64`, `linux-arm64`, `linux-armhf`, `alpine-x64`,
`alpine-arm64`, `darwin-x64`, `darwin-arm64`.

> **Note**: This explicitly excludes `web` (vscode.dev / github.dev), as the extension requires a Node.js runtime and
> child process execution to run the BURP server.

The standalone `burp.jar` is excluded via `.vscodeignore` to keep packages under 10MB. Users download the matching
engine on-demand on first start.

---

## Vendored `baukasten-ui` Dependency

The webview uses the `<Tree />` component from [TypeFox/baukasten PR #44](https://github.com/TypeFox/baukasten/pull/44).
Because this feature has not yet been released to npm (the latest npm release is `0.3.0`), a pre-built package tarball
is temporarily vendored at `vendor/baukasten-ui-0.3.0.tgz` and installed via
`"baukasten-ui": "file:./vendor/baukasten-ui-0.3.0.tgz"` in `package.json`.

To rebuild or refresh this vendored tarball from upstream source:

```bash
git clone https://github.com/TypeFox/baukasten.git
cd baukasten

npm ci
npm run build:baukasten

mkdir -p /path/to/BURP/rml-dev-server/vendor
npm pack packages/baukasten --pack-destination /path/to/BURP/rml-dev-server/vendor

cd /path/to/BURP/rml-dev-server
npm install
```

> **TODO / Next Release**: Once TypeFox publishes an official npm release containing PR #44 (e.g. `^0.4.0`), remove
> `vendor/baukasten-ui-0.3.0.tgz` and revert `package.json` to the standard npm dependency.
