# BURP-Error: A Basic and Unassuming RML Processor with RML Execution Report

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.19455346.svg)](https://doi.org/10.5281/zenodo.19455346)

BURP (Basic and Unassuming RML Processor) is a reference implementation for the
new [RML specification](http://w3id.org/rml/portal) which has been written from scratch to have no influence from prior
implementations of RML.
BURP was created to serve as a reference RML implementation for the Knowledge Graph Construction community and to verify
the RML specifications, their feasibility, and coverage of their test cases.

BURP-Error is a fork of BURP that adds error handling to the RML processor, according to
the [RML Execution Report](https://w3id.org/dre/rer). The project is a proof of concept, no maintenance guaranteed, but
if you have any questions or issues, feel free to
[open an issue on GitHub](https://github.com/kg-construct/BURP/issues/new).

> [!WARNING]
> Compliance to the specifications and correctness of the result are the main goals of BURP, not performance.

## Coverage matrix

| RML module                                           | Test Cases: Pass / Fail / Total |
|------------------------------------------------------|---------------------------------|
| [RML-Core](https://w3id.org/rml/core/spec)           | ✅ 76 / 0 / 76                   |
| [RML-IO](https://w3id.org/rml/io/spec)               | ✔️ 56 / 0 / 56*                 | 
| [RML-IO-Registry](https://w3id.org/rml/io-registry/) | 🚧️ 52 / 50 / 102 Details below  | 
| [RML-CC](https://w3id.org/rml/cc/spec)               | ✅ 35 / 0 / 35                   | 
| [RML-FNML](https://w3id.org/rml/fnml/spec)           | ✅️ 20 / 0 / 20                  | 
| [RML-Star](https://w3id.org/rml/star/spec)           | ❌ Not implemented               | 
| [RML-LV](https://w3id.org/rml/lv/spec)               | ✅️ 41 / 0 / 41                  |
| [RER](https://w3id.org/dre/rer)                      | 🪅 Demo implementation          |

\* We consider 17 test cases in RML-IO to have issues. 

### RML-IO-Registry coverage details

BURP natively supports the following input sources:

- `rml:FilePath` or `rml:RelativePathSource` — local files (supports `rml:root` and `rml:path`)
- `rml:CSV` — CSV files (including CSVW tables and their dialects: encoding, delimiter, header, nulls)
- `rml:JSONPath` — JSON sources ([RFC 9535](https://www.rfc-editor.org/rfc/rfc9535) JSONPath iterator)
- `rml:XPath` — XML sources (XPath 1.0 iterator; supports namespace/prefix mappings for XPath reference formulations)
- `rml:SPARQL Results (CSV/TSV/XML/JSON)` — SPARQL result files, SPARQL endpoints/services, and data dumps (VOID/SD)
- `rml:SQL2008Query` and `rml:SQL2008Table` — relational database sources (via D2RQ properties such as `d2rq:jdbcDSN`,
  `d2rq:jdbcDriver`, username, password)
- DCAT Distribution / CSVW Table — remote files via DCAT `downloadURL` or CSVW `url`

Extensible architecture: custom sources, reference formulations, and functions can be plugged in programmatically.

## About the RML Execution Report

The RML Execution Report (RER) is a taxonomy for describing the execution of an RML mapping, defined at [https://w3id.org/dre/rer](https://w3id.org/dre/rer).

BURP-Error can output the execution report using the `rer` vocabulary.
In addition, if your mapping is in Turtle format, BURP-Error will also output the execution report in a textual format
with code snippets of your mappings instead of RDF 1.2 statement pointers ([ptr](https://w3id.org/dre/ptr)).

> [!NOTE]
> To locate the precise Turtle token coordinates, we use a custom Turtle parser (in `turtleprov`) that admittedly passes
only 39/103 (37%) of RDF 1.2 test cases and 217/313 (69%) of RDF 1.1 test cases.

## Using the BURP CLI

Run using [JBang](https://www.jbang.dev/download/):

```bash
jbang burp@kg-construct -m <mappingFile> [-o <outputFile>] [-b <baseIRI>]
```

Download the fat JAR from [GitHub Releases](https://github.com/kg-construct/BURP/releases), and run with Java 21+:

```bash
java -jar burp.jar -m <mappingFile> [-o <outputFile>] [-b <baseIRI>]
```

If no `-o` / `--output` file is provided and the mapping does not specify RML-IO targets, the output is written to
standard output (`stdout`).

## Using BURP as a Library

Available on [Maven Central](https://central.sonatype.com/artifact/io.github.kg-construct/burp):

**Maven (`pom.xml`):**

```xml
<dependency>
    <groupId>io.github.kg-construct</groupId>
    <artifactId>burp</artifactId>
    <version>0.1.4</version>
</dependency>
```

**Gradle (Kotlin DSL):**

```kotlin
implementation("io.github.kg-construct:burp:0.1.4")
```

## Using the RML Dev Server (VS Code Extension)

Install the extension from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=uliege-dre.rml)
or [Open VSX](https://open-vsx.org/extension/uliege-dre/rml).

Features while editing RML mappings in Turtle:
- Static validation of RML mappings
- Live evaluation of non-constant terms directly inside the editor
- Evaluate RML nodes with custom sample data
- RML-FNML function and parameter auto-completion
- RDF graph visualization
- Fast mapping execution with caching and result table/graph preview
- Preview source iterations in a table (for tabular data) or tree view (for hierarchical sources)
- View execution traces

Telemetry is collected to improve extension features and diagnostics.

## Building BURP

To build the project:

```bash
mvn package -DskipTests
```

To build the deployable standalone fat JAR (`target/burp.jar`):

```bash
mvn package -Pdeploy -DskipTests
```

> [!NOTE]
> Unit tests require Docker to run RML-IO-Registry tests using test-containers.

## Citation

If you use BURP-Error, please cite our paper:

```
@inproceedings{duchateau2026rml-execution-report,
  author       = {Jakub Duchateau and Dylan {Van Assche} and Christophe Debruyne},
  editor       = {},
  title        = {Beyond Exit Code 1: A Vocabulary for Execution Report of RML Processors (RER)},
  booktitle    = {Proceedings of the 7th International Workshop on Knowledge Graph Construction
                  co-located with 23rd Extended Semantic Web Conference ({ESWC} 2026),
                  Dubrovnik, Croatia, May 10, 2026},
  series       = {{CEUR} Workshop Proceedings},
  volume       = {X},
  publisher    = {CEUR-WS.org},
  year         = {2026},
  url          = {https://ceur-ws.org/Vol-X/paperX.pdf}
}
```

If you use BURP, please cite our paper:

```
@inproceedings{DBLP:conf/kgcw/AsscheD24,
  author       = {Dylan {Van Assche} and Christophe Debruyne},
  editor       = {David Chaves{-}Fraga and Anastasia Dimou and
                  Ana Iglesias{-}Molina and Umutcan Serles and
                  Dylan {Van Assche}},
  title        = {BURPing Through {RML} Test Cases},
  booktitle    = {Proceedings of the 5th International Workshop on Knowledge Graph Construction
                  co-located with 21th Extended Semantic Web Conference ({ESWC} 2024),
                  Hersonissos, Greece, May 27, 2024},
  series       = {{CEUR} Workshop Proceedings},
  volume       = {3718},
  publisher    = {CEUR-WS.org},
  year         = {2024},
  url          = {https://ceur-ws.org/Vol-3718/paper4.pdf}
}
```

## License

BURP is released under the [MIT license](./LICENSE).
