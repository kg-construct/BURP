## RMLSTARTC004b

**Title**: two-level quoted triple in subject and object, two sources

**Description**: Tests the creation of two-level recursiveness of asserted quoted triples as first as object, second as subject with data from two sources

**Error expected?** No

**Input**
```
c1-1,c1-2,c1-3,c1-4
s,o,a,1

```

**Input 1**
```
c2-1,c2-2
z,1

```

**Mapping**
```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rml: <http://w3id.org/rml/> .
@prefix ex: <http://example/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix : <http://example.org/> .
@base <http://example.org/> .

:firstTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data1.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c1-1}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p ;
        rml:objectMap [
            rml:template "http://example/{c1-2}"
        ]
    ] .

:secondTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data1.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c1-3}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q ;
        rml:objectMap [
            rml:quotedTriplesMap :firstTM
        ]
    ] .

:thirdTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data2.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:quotedTriplesMap :secondTM;
        rml:joinCondition [
          rml:child "c2-2" ;
          rml:parent "c1-4" ;
        ];
    ];
    rml:predicateObjectMap [
        rml:predicate ex:r ;
        rml:objectMap [
            rml:template "http://example/{c2-1}"
        ]
    ] .

```

**Output**
```
<http://example/s> <http://example/p> <http://example/o> .
<http://example/a> <http://example/q> << <http://example/s> <http://example/p> <http://example/o> >> .
<< <http://example/a> <http://example/q> << <http://example/s> <http://example/p> <http://example/o> >> >> <http://example/r> <http://example/z> .

```

