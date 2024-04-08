# BURP: A Basic and Unassuming RML Processor

## Building and using the code

To build the project and copy its dependencies, execute

```bash
$ mvn package 
$ mvn dependency:copy-dependencies
```

You can add `-DskipTests` after `mvn package` to skip the unit tests. The tests do rely on Docker for testing mappings on top of MySQL and PostgreSQL.

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