## RMLSTARTC001a

**Title**: quoted triple in subject, one source, blank node in quoted subject

**Description**: Tests the creation of a asserted quoted triple (with a blank node as subject) in the position of subject with data from one file

**Error expected?** No

**Input**
```
c1,c2,c3
b0,o,ABC
```

**Mapping**
```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rml: <http://w3id.org/rml/> .
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
        rml:reference "c1" ;
        rml:termType rml:BlankNode
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p ;
        rml:objectMap [
            rml:template "http://example/{c2}"
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
            rml:reference "c3"
        ]
    ] .

```

**Output**
```
_:b0 <http://example/p> <http://example/o> .
<< _:b0 <http://example/p> <http://example/o> >> <http://example/q> "ABC" .

```

