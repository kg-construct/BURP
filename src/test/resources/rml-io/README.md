# IO Test Cases

## Updating the metadata

The test cases publication (html pages) can be generated as followed:

1. Add the testcase description in `descriptions.csv` or fetch it from the [Google spreadsheet](https://docs.google.com/spreadsheets/d/1Ui216z2cF8bNAbdZvws-JoAhcjj4M2k_NlfzmCh1jh8/edit?gid=135246907#gid=135246907).
2. Execute the `make-metadata.py` script: `./make-metadata.py http://w3id.org/rml/io/`
   (This is based on the content of the folders with the test cases, and on the file descriptions.csv for the descriptions of the cases)
3. Download burp: `curl -LO https://github.com/kg-construct/BURP/releases/download/v0.1.1/burp.jar`
4. Generate the manifest with [Burp](https://github.com/kg-construct/BURP): `java -jar burp.jar -m manifest.rml.ttl -o manifest.ttl -b http://w3id.org/rml/io/test/`. Optionally, sort the output: `sort -o manifest.ttl manifest.ttl`
5. Run list.sh and insert output in dev.html
6. Set the `prevVersion` in config.js
7. To publish the new HTML verson of the test cases, export `dev.html` as `index.html` in ./docs and in a subfolder with the date of the publication (maybe adapt the publication date)
