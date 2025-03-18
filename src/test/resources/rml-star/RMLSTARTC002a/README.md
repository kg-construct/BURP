## RMLSTARTC002a

**Title**: quoted triple in subject, one source, blank node in quoted object

**Description**: Tests the creation of a asserted quoted triple (with a blank node as object) in the position of subject with data from one file

**Error expected?** No

**Input**
```
c1,c2,c3
s,b1,456
```

**Mapping**
```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rml: <http://w3id.org/rml/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <http://example/> .
@prefix : <http://example.org/> .
@base <http://example.org/> .

:firstTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c1}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p ;
        rml:objectMap [
            rml:reference "c2" ;
            rml:termType rml:BlankNode
        ]
    ] .

:secondTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:quotedTriplesMap :firstTM
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q ;
        rml:objectMap [
            rml:reference "c3";
            rml:datatype xsd:integer
        ]
    ] .

```

**Output**
```
<http://example/s> <http://example/p> _:b1 .
<< <http://example/s> <http://example/p> _:b1 >> <http://example/q> "456"^^<http://www.w3.org/2001/XMLSchema#integer> .

```

