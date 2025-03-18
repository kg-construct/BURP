## RMLSTARTC007b

**Title**: non-asserted quoted triples in subject and object, two sources

**Description**: Tests the creation of a two non-asserted quoted triples, one in the position of subject and other in object with data from two sources

**Error expected?** No

**Input**
```
c1-1,c1-2,c1-3
s1,o1,1

```

**Input 1**
```
c2-1,c2-2,c2-3
s2,o2,1

```

**Mapping**
```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rml: <http://w3id.org/rml/> .
@prefix ex: <http://example/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix : <http://example.org/> .
@base <http://example.org/> .

:firstTM a rml:NonAssertedTriplesMap ;
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
        rml:predicate ex:p1 ;
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
              rml:child "c2-3" ;
              rml:parent "c1-3" ;
            ];
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q; ;
        rml:objectMap [
            rml:quotedTriplesMap :thirdTM
        ]
    ] .

:thirdTM a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data2.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c2-1}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p2 ;
        rml:objectMap [
            rml:template "http://example/{c2-2}"
        ]
    ] .

```

**Output**
```
<< <http://example/s1> <http://example/p1> <http://example/o1> >> <http://example/q> << <http://example/s2> <http://example/p2> <http://example/o2> >> .

```

