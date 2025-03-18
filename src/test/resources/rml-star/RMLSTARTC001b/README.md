## RMLSTARTC001b

**Title**: quoted triple in subject, two sources, blank node in quoted subject

**Description**: Tests the creation of a asserted quoted triple (with a blank node as subject) in the position of subject with data from two files

**Error expected?** No

**Input**
```
c1-1,c1-2,c1-3
b0,o,1

```

**Input 1**
```
c2-1,c2-2
ABC,1

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
            rml:path "data1.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:reference "c1-1" ;
        rml:termType rml:BlankNode
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
            rml:path "data2.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:quotedTriplesMap :firstTM ;
        rml:joinCondition [
          rml:child "c2-2" ;
          rml:parent "c1-3" ;
        ];
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q ;
        rml:objectMap [
            rml:reference "c2-1"
        ]
    ] .

```

**Output**
```
_:b0 <http://example/p> <http://example/o> .
<< _:b0 <http://example/p> <http://example/o> >> <http://example/q> "ABC" .

```

