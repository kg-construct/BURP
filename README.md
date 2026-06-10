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
[open an issue on GitHub](https://github.com/jduchateau/BURP/issues/new).

Warning: Joins are quadratic, in terms of iterations and number of multivalued join conditions values.

## Coverage matrix

| RML module                                           | Test Cases: Pass / Fail / Total |
|------------------------------------------------------|---------------------------------|
| [RML-Core](https://w3id.org/rml/core/spec)           | ✔️ 76 / 0 / 76                  |
| [RML-IO](https://w3id.org/rml/io/spec)               | 🚧 54 / 19 / 73                 | 
| [RML-IO-Registry](https://w3id.org/rml/io-registry/) | ✔️ 52 / 50 / 102 Details below  | 
| [RML-CC](https://w3id.org/rml/cc/spec)               | ✅ 35 / 0 / 35                   | 
| [RML-FNML](https://w3id.org/rml/fnml/spec)           | ✅️ 19 / 1 / 20                  | 
| [RML-Star](https://w3id.org/rml/star/spec)           | ❌ Not implemented               | 
| [RML-LV](https://w3id.org/rml/lv/spec)               | ✅️ 41 / 0 / 41                  |
| [RER](https://w3id.org/dre/rer)                      | 🪅 Demo implementation          |

### RML-IO-Registry coverage details

BURP supports natively the following input sources:

- rml:FilePath or rml:RelativePathSource — local files (supports rml:root and rml:path)
- rml:CSV — CSV files (including CSVW tables and their dialects: encoding, delimiter, header, nulls)
- rml:JSONPath — JSON sources ([RFC 9535](https://www.rfc-editor.org/rfc/rfc9535) JSONPath iterator)
- rml:XPath — XML sources (XPath 1.0 iterator; supports namespace/prefix mappings for XPath reference formulations)
- rml:SPARQL Results (CSV/TSV/XML/JSON) — SPARQL result files, SPARQL endpoints/services, and data dumps (VOID/SD)
- rml:SQL2008Query and rml:SQL2008Table — relational database sources
  (via D2RQ properties such as d2rq:jdbcDSN, d2rq:jdbcDriver, username, password)
- DCAT Distribution / CSVW Table — remote files via DCAT downloadURL or CSVW url

- Extensions possible see extending BURP

## About the RML Execution Report

The RML Execution Report (RER) is a taxonomy for describing the execution of an RML mapping, defined at [https://w3id.org/dre/rer](https://w3id.org/dre/rer).

BURP-Error can output the execution report using the `rer` vocabulary.
In addition, if your mapping is in Turtle format, BURP-Error will also output the execution report in a textual format with code snippets or your mappings instead of the rdf 1.2 statements pointers ([ptr](https://w3id.org/dre/ptr)).

Notice; to locate prescicely the turtle token location, we use a custom turtle parser (in `turtleprov`) that admitedly is only 39/103 (37%) of RDF1.2 test cases and 217/313 (69%) of RDF1.1 test cases.

## Building BURP

To build the project and copy its dependencies, execute

```bash
$ mvn package 
$ mvn dependency:copy-dependencies
```

You can add `-DskipTests` after `mvn package` to skip the unit tests. The tests do rely on Docker for testing mappings on top of MySQL and PostgreSQL.

Some libraries are available on GitHub's maven repository. Add a GitHub authentication token to your Apache Maven `settings.xml` file. More information can be found [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry).

## Using BURP

You can run BURP instantly without installing Java manually or downloading any files using [JBang](https://jbang.dev):

```bash
$ jbang burp@jduchateau [-h] [-b=<baseIRI>] -m=<mappingFile> [-o=<outputFile>]
```

*Note: JBang will automatically download the required JDK and resolve all project dependencies on the first run.*

If you don't have JBang installed yet, see the [JBang Installation Guide](https://jbang.dev/download) to install it.

Alternatively, if you prefer to run it using a local pre-built fat JAR and standard Java, execute the following command:

```bash
$ java -jar burp.jar [-h] [-b=<baseIRI>] -m=<mappingFile> [-o=<outputFile>]
```
A fat jar is also provided with the [Apache Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/). It does not depend on the `dependency` folder.

```
Usage: burp [-h] [-b=<baseIRI>] -m=<mappingFile> [-o=<outputFile>]
  -b, --baseIRI=<baseIRI>   Used in resolving relative IRIs produced by the RML mapping
  -h, --help                Display a help message
  -m, --mappingFile=<mappingFile>
                            The RML mapping file
  -o, --outputFile=<outputFile>
                            The output file
```

If no outputFile is provided and the RML mapping does not rely on RML-IO for targets, then the output is written to the standard output.

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
