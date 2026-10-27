# RML Dev Server

A Visual Studio Code extension for the **RDF Mapping Language (RML)**, powered by the [BURP](https://github.com/kg-construct/BURP) RML processor.

## Features

- **Live Evaluation & Inlay Hints**: Inspect evaluated terms and expressions inline directly within Turtle / RML files.
- **Plan Node Hierarchy View**: Explore logical execution plans generated from RML mappings.
- **Trace & Execution Matrix**: Inspect intermediate evaluation traces, records, and performance metrics.
- **Evaluator & Value Inspector**: Interactively evaluate selectors, expressions, and references against data sources.
- **RDF Visualizer**: View output RDF graphs and tabular representations with customizable hull clustering.
- **Integrated Server**: Bundled directly with the extension (`server/burp.jar`), with support for custom builds and
  external instances.

## How to use

Install the extension from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=uliege-dre.rml)
or [Open VSX](https://open-vsx.org/extension/uliege-dre/rml).

A Java 21+ runtime is required to execute the BURP processor (`java` command available on your `PATH`).
If no compatible Java runtime is found on your system, the extension can automatically configure one for you.

BURP comes pre-bundled with the extension in `server/burp.jar` and can be customized via the extension settings below
(e.g. to point to a custom JAR build or external instance).

## Extension Settings

This extension contributes the following settings:

* `rml.burp.path`: Path to an explicit custom BURP JAR file (e.g. `/path/to/target/burp.jar`). If left empty, the
  bundled `server/burp.jar` will be used automatically.
* `rml.java.path`: Path to the Java executable used to run BURP (default: `java`).
* `rml.server.spawnProcess`: Whether to spawn the BURP server process automatically (default: `true`).
* `rml.server.port`: Dev server port number (default: `0` for dynamically assigned port).
* `rml.server.autoStart`: Automatically start the BURP Dev Server on extension activation (default: `true`).
* `rml.server.extraArgs`: Extra command-line arguments to pass to BURP when spawning the server.
* `rml.baseIRI`: Base IRI used to resolve relative IRIs produced by the mapping.
* `rml.graph.backgroundMethod`: Background envelope algorithm for non-default named RDF graphs.

## Development

For setup, local debugging, and packaging instructions, see the [Developer Guide](./dev.md).

## License

MIT - see the [LICENSE](https://github.com/kg-construct/BURP/blob/main/LICENSE) file for details.
