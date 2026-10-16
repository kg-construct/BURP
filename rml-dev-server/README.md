# RML Dev Server

A Visual Studio Code extension for the **RDF Mapping Language (RML)**, powered by the [BURP](https://github.com/kg-construct/BURP) RML processor.

## Features

- **Live Evaluation & Inlay Hints**: Inspect evaluated terms and expressions inline directly within Turtle / RML files.
- **Plan Node Hierarchy View**: Explore logical execution plans generated from RML mappings.
- **Trace & Execution Matrix**: Inspect intermediate evaluation traces, records, and performance metrics.
- **Evaluator & Value Inspector**: Interactively evaluate selectors, expressions, and references against data sources.
- **RDF Visualizer**: View output RDF graphs and tabular representations with customizable hull clustering.
- **Integrated Server**: Runs out-of-the-box by fetching the BURP processor on demand, with support for local builds and
  external instances.

## How to use

Install the extension from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=uliege-dre.rml)
or [Open VSX](https://open-vsx.org/extension/uliege-dre/rml).

A Java 21+ runtime is required to execute the BURP processor (`java` command available on your `PATH`).
If no compatible Java runtime is found on your system, the extension can automatically download and configure one for
  you.

BURP will be downloaded by the extension on the first activation if not available
in [extension folder](https://code.visualstudio.com/docs/configure/extensions/extension-marketplace#_where-are-extensions-installed)
`/`.

## Extension Settings

This extension contributes the following settings:

* `rml.burp.path`: Path to an explicit BURP JAR or executable. If left empty, the auto-detected local build, downloaded
  JAR, or `burp` on your system `PATH` will be used automatically.
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
