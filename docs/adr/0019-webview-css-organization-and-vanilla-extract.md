# ADR-0019: Webview CSS Organization and Vanilla Extract Evaluation

## Status

Accepted

## Context

The `rml-dev-server` VS Code extension provides several interactive webview panels:
- **Evaluator & Value Inspector** (`evaluatorView.ts`)
- **Plan Node Hierarchy / Structure View** (`structureView.ts` / `PlanApp.tsx`)
- **RDF Graph Visualizer & Table** (`rdfVisualizerManager.ts`)
- **Iteration & Quad Trace Flow** (`traceProvider.ts` / `TraceNodeDetailsCard.tsx`)

### Current Styling Architecture
All webview components consume the **Baukasten UI design system** (`baukasten-ui`), which maps host CSS variables into a unified semantic token namespace (`--bk-*`, such as `--bk-color-primary`, `--bk-color-border`, `--bk-color-hover`, `--bk-opacity-muted`, `--bk-font-size-sm`).

Currently, webview styling is organized as follows:
1. **Base Styles**: `baukasten-base.css` and `baukasten-vscode.css` imported at webview entry.
2. **Local Component Styles**: Plain CSS stylesheet (`styles.css`) referencing `--bk-*` CSS variables, avoiding hard-coded hex colors or direct `--vscode-*` dependencies.
3. **Bundling Pipeline**: Plain `esbuild` (`build:webview` in `package.json`), which bundles `index.tsx` into `out/webview.js` and compiles `.css` imports into `out/webview.css` in sub-second builds.

### The Question: Adopting Vanilla Extract
Upstream Baukasten UI components (including the new `Tree` component in PR #44) are implemented with **Vanilla Extract** (`@vanilla-extract/css` and `@vanilla-extract/recipes`), defining styles in `.css.ts` files with type-safe recipes and variants.

Adopting Vanilla Extract in `rml-dev-server` would offer:
- Zero-runtime TypeScript CSS with static CSS extraction.
- Fully typed variants and style recipes matching upstream Baukasten patterns.
- Colocation of styles with components (`MyComponent.css.ts`).

However, adopting Vanilla Extract locally requires:
- Integrating `@vanilla-extract/esbuild-plugin` or migrating webview bundling to Vite.
- Additional build-time dependencies and complexity in the VS Code extension toolchain.
- Careful handling of theme variable indirection and source maps inside VS Code webview sandboxes.

## Decision Drivers

- **Simplicity and Build Speed**: Keep webview build and packaging fast, lightweight, and robust with minimal configuration overhead.
- **Strict Token Adherence**: Guarantee that all styles adapt seamlessly across VS Code, Eclipse Theia, and browser preview environments using `--bk-*` semantic tokens.
- **Consumption vs Authoring Separation**: Consume upstream Baukasten UI components as pre-compiled library artifacts rather than compiling raw upstream `.css.ts` source in our extension pipeline.
- **Extensibility**: Provide a clear migration path toward Vanilla Extract if local styling complexity warrants type-safe recipes.

## Decision

1. **Retain Plain CSS with `--bk-*` Semantic Design Tokens for Extension Components**:
   - Keep `styles.css` as the styling baseline for `rml-dev-server` webview components.
   - Enforce the rule that all CSS classes consume `--bk-*` CSS variables exclusively (e.g. `--bk-color-border`, `--bk-color-hover`, `--bk-opacity-muted`, `--bk-radius-sm`).
2. **Consume Upstream Baukasten Components Pre-Compiled**:
   - Consume Baukasten UI components (including `Tree` from PR #44) via pre-built library packages or vendored `.tgz` distribution tarballs built through Baukasten's native Vite + Vanilla Extract pipeline.
   - Do not require `rml-dev-server`'s `esbuild` pipeline to process `.css.ts` files directly.
3. **Future Vanilla Extract Migration Trigger**:
   - If local webview UI complexity grows to require extensive variant recipes or dynamic styling props, evaluate adding `@vanilla-extract/esbuild-plugin` or adopting Vite in a dedicated infrastructure update.

## Consequences

### Positive
- **Instant Bundling**: `esbuild` continues to bundle the entire webview bundle and CSS in ~100ms with zero extra loader dependencies.
- **Clean Theme Integration**: Full compatibility with VS Code themes and Eclipse Theia via `--bk-*` tokens.
- **Standard Library Consumption**: Using pre-built Baukasten packages ensures 100% fidelity with upstream releases.

### Negative / Trade-offs
- Local extension components do not have compile-time TypeScript type checking on CSS class names or recipe variants.
