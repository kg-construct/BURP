# Automated Demo Recordings

Scripts in `src/demo/` use `vscode-extension-tester` (`extest`) and `ffmpeg` to record reproducible `.mp4` videos,
`.gif` animations, and `.annotations.json` timing/coordinate sidecars into `rml-dev-server/demos/`.

## Prerequisites

- `ffmpeg` installed on your `PATH`.
- Run the one-time `extest` setup from `rml-dev-server/`:
  ```bash
  npm run ui-test:setup
  ```
- **One-time SWLS (`semantic-web-lsp`) server setup**: On the first run, `ajuvercr.semantic-web-lsp` prompts via a
  notification to install its language server and reload. Accept the install/reload once so subsequent `demo:record`
  runs start cleanly without notifications.

## Recording Demos

From `rml-dev-server/`:

```bash
npm run demo:record
```
