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

To produce a single universal `.vsix` package:

```bash
# Create the fat jar
mvn package -Pdeploy -DskipTests

# Copy it
cp target/burp.jar rml-dev-server/server/burp.jar

# Package the extension
npm --prefix rml-dev-server run package
```

It seems the size limit for extensions was lift off, so excluding  `burp.jar` is unnecessary.


