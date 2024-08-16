# BURP: A Basic and Unassuming RML Processor

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.11037712.svg)](https://doi.org/10.5281/zenodo.11037712)


BURP (Basic and Unassuming RML Processor) is a reference implementation for the new [RML specification](http://w3id.org/rml/portal) which has been written from scratch to have no influence from prior implementations of RML.
BURP was created to serve as a reference RML implementation for the Knowledge Graph Construction community and to verify the RML specifications their feasibility and coverage of their test cases.

## Coverage matrix

| [RML-Core](http://w3id.org/rml/core/spec) | [RML-IO](http://w3id.org/rml/io/spec) | [RML-CC](http://w3id.org/rml/cc/spec) | [RML-FNML](http://w3id.org/rml/fnml/spec) | [RML-Star](http://w3id.org/rml/star/spec) |
| ----------------------------------------- | ------------------------------------- | ------------------------------------- | ----------------------------------------- | ----------------------------------------- |
| ✔️ 100% coverage                          | 🚧 Source yes, Target WIP              | ✔️ 100% coverage                      | ✔️ 100% coverage                          | 🚧 WIP                                     |

## Building BURP

To build the project and copy its dependencies, execute

```bash
$ mvn package 
$ mvn dependency:copy-dependencies
```

You can add `-DskipTests` after `mvn package` to skip the unit tests. The tests do rely on Docker for testing mappings on top of MySQL and PostgreSQL.

## Using BURP

The run the R2RML processor, execute the following command:

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
