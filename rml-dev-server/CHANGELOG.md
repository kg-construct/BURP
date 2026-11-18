# Change Log

All notable changes to the **RML Dev Server** (`uliege-dre.rml`) VS Code extension are documented in this file.

## [0.0.4] - 2026-11-28

### Added

- Unified RML error and warning display across both the **Run Mapping** execution view and the **Evaluator & Value
  Inspector** sidebar, always accompanied by structured RER error codes instead of raw string diagnostics.
- Improved README with setup instructions, telemetry disclosure, and demo GIFs.

### Changed

- Delegated autocompletion, CodeLens actions, and validation diagnostics to standard LSP integration for cleaner and
  more reliable editor behavior.
- Upgraded extension dependencies.

## [0.0.3] - 2026-11-12

### Added

- **Ontology-backed validation & completion**: Configure OWL/RDFS ontologies via `rml.ontologies` (or `owl:imports`) to
  catch predicate/datatype mismatches and autocomplete vocabulary terms.
- **Coherent inline evaluation & iteration stepping**: Step through source iterations (`Alt+[` / `Alt+]`) across joined
  mappings while keeping inline hints and the Evaluator & Value Inspector in sync.
- **Implicit JSON reference schema deduction**: Automatically infer reference paths and sample values for autocompletion
  (configurable via `rml.iterationSampleLimit`).

### Changed

- Simplified commands, views, and settings under the `rml.*` prefix.
- Automatically allocate a free port when starting the bundled server (reserving `rml.server.port` for external server
  connections).
- Removed automatic runtime JAR downloads from GitHub in favor of the bundled processor.

## [0.0.2] - 2026-10-16

### Changed

- Bundled the BURP processor (`server/burp.jar`) and webview assets directly inside the extension package for
  zero-setup, offline startup.
- Verified backend server version compatibility on startup independently from the extension version.

## [0.0.1] - 2026-10-14

### Added

- Initial release of the RML Dev Server VS Code extension powered by BURP:
  - Live inline evaluation hints, RML/FNML validation diagnostics, and context-aware autocompletion.
  - **Evaluator & Value Inspector** sidebar and Logical Source / Logical View data preview CodeLenses.
  - **Run Mapping** execution with filterable Table views (Flat, Group by Subject, Group by Iteration) and an
    interactive **RDF Graph** visualizer.
  - Iteration dataflow and output quad provenance tracing.
