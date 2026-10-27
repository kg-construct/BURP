# BURP & RML Dev Server Guidelines

## Build & Testing

- Prefer built-in tools and mcp such as idea mcp to search execute debug refactor navigate code, when command line tools are needed, always prefix command line operations with `rtk` (e.g., `rtk mvn test -Dtest=<TestClass>`,
  `rtk uv run scripts/combine_modules.py --all`).

- **Submodules**: You may need to update and initialise submodules when working from a worktree.

```bash
git submodule update --init --recursive
```

- **Packaging for VS Code Extension**: The `rml-dev-server` extension and its test suite expect the unversioned fat JAR
  located at `target/burp.jar`. Always build using the `deploy` Maven profile:
  ```bash
  mvn package -Pdeploy -DskipTests
  ```
  Do not use standard `mvn package` without `-Pdeploy` when preparing binaries for the extension.

- **Vendored `baukasten-ui` for Webview**: `rml-dev-server` temporarily vendors `vendor/baukasten-ui-0.3.0.tgz` to consume the `<Tree />` component from [TypeFox/baukasten PR #44](https://github.com/TypeFox/baukasten/pull/44) ahead of its upstream npm release. Standard `npm install` inside `rml-dev-server` resolves this local tarball. When TypeFox publishes a release containing PR #44 (e.g. `^0.4.0`), remove `vendor/` and restore the standard npm dependency in `package.json`.

- To force Maven to copy and synchronize updated resources to the target directory without a full recompilation of
  source code, run:
  `mvn resources:testResources`
- Alternatively, perform a full build cleanup:
  `mvn clean test-compile` or `mvn clean test`

- Use the `idea` MCP server tools where appropriate:
    - Run `idea:get_file_problems` to verify that modified or new Java files have no inspection errors.
    - Use run configurations and debug features for targeted test debugging.
    - **Debug First on Failure**: When a test fails, do not guess fixes blindly. Use `idea` run configurations or the debugger first to inspect the actual execution values, stack traces, and data flow.
    - **Critique of Test Assertions**: Distinguish between broken core logic and brittle assertion details (e.g., prefix compaction differences `ex:item` vs `<http://example.org/item>`, exact string quotation). If the core logic is correct but the assertion checks obsolete formatting details, update the test assertion accordingly.

- **Test Fixtures & Upstream Submodules**:
  - Keep test cases in `rml-modules/*` submodules clean and identical to upstream (except when explicitly asked to fix a test).
  - When testing error diagnostics, malformed mappings, or invalid references, place mapping files in repository-local test resources (e.g., `src/test/resources/...`) with explicit, descriptive tokens (e.g., `InvalidChild`, `InvalidParent`).

- **Idiomatic AssertJ Assertions**:
  - Avoid manual streaming (`.stream().filter(...).toList()`) in test assertions.
  - Use AssertJ's fluent filtering and extraction directly:
    ```java
    assertThat(diagnostics)
            .filteredOn(d -> RER.ReferenceFormulationSyntaxError.getURI().equals(d.rerCodeUri()))
            .extracting(d -> tuple(d.range().getStart().getLine(), d.range().getStart().getCharacter()))
            .containsExactlyInAnyOrder(tuple(21, 25), tuple(22, 26));
    ```

## Codebase Refactoring & Architecture

- **Modern Java Idioms & Clean Models**:
  - Prefer Java `record`s for immutable domain objects, DTOs, and graph nodes/values.
  - Use pattern-matching `switch` expressions (`case SubType st -> ...`) instead of cascading `instanceof` / cast blocks.
  - Actively eliminate code duplication by extracting shared evaluation and collection logic into focused, descriptively named helper methods.

- **Human-in-the-Loop Collaboration**:
  - When encountering non-trivial architectural ambiguities, model changes, or complex debugging failures, pause to analyze and explain the root cause clearly to the user before attempting speculative rewrites.

- **Markdown Documentation Style**:
  - Use Java's modern Markdown documentation comments (`///`, JEP 467) for class and API documentation.
  - For complex subsystems, provide an architectural overview, design rationale ("why it is there"), and a "Reading the Code" section highlighting key entry points.

- **No Legacy Compatibility for Pre-Release APIs**: When refactoring or generalizing internal APIs and models, do not
  introduce or retain backward-compatibility constructors or deprecated adapters. Update all existing callers, services,
  and tests to use the new generalized API directly.

- Multi-line Strings: Always use Java's text block feature (`""" ... """`) when writing multi-line text (e.g., JSON
  payloads, XML snippets, or queries).
- Avoid using long fully qualified types in source code, use the simple class name instead:
  ```java
  // Correct:
  import com.google.gson.Gson;
  //...
  Gson gson = new Gson();
  ```

- Null Safety for new or heavily modified code:
    - Annotate all new or significantly modified classes, records, and interfaces with `@NullMarked`
      (`org.jspecify.annotations.NullMarked`).
    - Explicitly annotate any nullable parameter, field, or return type with `@Nullable`
      (`org.jspecify.annotations.Nullable`).

- Avoid large monolithic methods.
    - Eliminate code duplication by extracting shared logic into focused, descriptively named helper methods (e.g.
      separate escaping, token scanning, error emission).
    - Keep method bodies concise and single-purpose.
- If `rml-dev-server/src/generated/types.ts` is only different due to the date in the top comment, ignore it while
  commiting.

- **Synthetic Statements & Provenance**:
  - Synthetic statements generated during shortcut expansion (e.g., `urn:burp:synth:`) are internal artefacts and must never be recorded as node origins or accessed statements.
  - Always wire the original mapping statement (`shortcutStmt`) directly to the instantiated AST nodes.
  - Do not add downstream defensive checks or filters for synthetic URIs; ensure the RDF loader establishes correct provenance at creation time.

- **Polymorphic Model Types & DevServer Serialization**:
  - When introducing a new class or record that implements a polymorphic hierarchy serialized over LSP/JSON-RPC (such as `RmlEvaluationResult`, `StructureSelector`, or `EvaluationDataContext`):
    1. **Register in DevServer**: Register the subtype and its discriminator label in `DevServer.configureGson(GsonBuilder)` using `RuntimeTypeAdapterFactory`.
    2. **TypeScript Generator**: Add the class pattern to `typescript-generator-maven-plugin` in `pom.xml` so `rml-dev-server/src/generated/types.ts` reflects the new model.
    3. **Webview Components**: Add the type interface, type guard, and token component to `rml-dev-server/src/webview/common/RdfToken.tsx` (or relevant webview views).
    4. **Serialization Test**: Verify in a unit test that `DevServer.configureGson(builder)` serializes the new type with its expected `"type"` discriminator.

## Error Handling

- Use specific RER error types (e.g., `ReferenceFormulationSyntaxError`, `IncorrectTermType`) rather than generic
  `MappingError`.
- Respect `ValidationMode`:
    - `STRICT`: throw `BurpException(error)` immediately upon encountering invalid mappings or shapes.
    - `LENIENT`: record the error into `RmlExecutionReport` without throwing; leave the erroneous component inert (e.g.
      empty template segments producing no terms) to avoid runtime failures.