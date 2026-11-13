# RML Dev Server - Developer Guide

This document describes how to set up, build, test, and package the RML Dev Server VS Code extension.

For general BURP information see the root [../dev.md](../dev.md).

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

Point VS Code to your local JAR in `.vscode/settings.json`:

```json
{
  "rml.burp.path": "${workspaceFolder}/target/burp.jar"
}
```

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

The extension bundles the backend `burp.jar` directly inside the `.vsix` package.

### 1. Versioning in `package.json`

Before packaging, verify the version configuration in `rml-dev-server/package.json`:

- **`version`**: The extension version published to the VS Code Marketplace and Open VSX.
  > **Note**: VS Code Marketplace requires strict `MAJOR.MINOR.PATCH` and rejects standard SemVer prerelease suffixes like `-alpha`, `-beta`, or `-rc`.
- **`serverVersion`**: The BURP backend version expected / bundled inside `server/burp.jar`.

### 2. Build and Bundle the Backend Fat JAR

```bash
# Build the shaded fat jar (from repo root)
mvn package -Pdeploy -DskipTests

# Copy to the extension server directory
mkdir -p rml-dev-server/server
cp target/burp.jar rml-dev-server/server/burp.jar
```

### 3. Package the VSIX

From the `rml-dev-server/` directory:

```bash
# Compile TypeScript and React webview assets
npm run compile
```

#### Pre-Release Package (Early Version)

Pass the `--pre-release` flag so the VSIX manifest is tagged for the pre-release channel:

```bash
npx vsce package --pre-release --out rml-dev-server-0.0.3.vsix
```

#### Stable Release Package

```bash
npx vsce package
```

### 4. Manual Store Upload

Upload the generated `.vsix` to the store portals:

1. **Visual Studio Marketplace**: Go to [marketplace.visualstudio.com/manage](https://marketplace.visualstudio.com/manage) $\to$ select publisher `uliege-dre` $\to$ **New Extension** $\to$ **Visual Studio Code** $\to$ upload `rml-dev-server-0.0.3.vsix`. (The Marketplace detects the `--pre-release` flag in the manifest automatically).
2. **Open VSX Registry**: Go to [open-vsx.org](https://open-vsx.org) $\to$ **Publish Extension** $\to$ upload `rml-dev-server-0.0.3.vsix`.



