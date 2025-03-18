## RMLSTARTC008a

**Title**: two-level non-asserted quoted triple  in subject and object two-fold, one source

**Description**: Tests the creation of a triple with non-asserted quoted triples as subject and object, both triples have in turn non-asserted quoted triples in subject and object with data form one source

**Error expected?** No

**Input**
```
c1,c2,c3,c4,c5,c6,c7,c8
s1,o1,s2,o2,s3,o3,s4,o4
```

**Mapping**
```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rml: <http://w3id.org/rml/> .
@prefix ex: <http://example/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix : <http://example.org/> .
@base <http://example.org/> .

:elementaryTM1 a rml:NonAssertedTriplesMap ;
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
        rml:predicate ex:p1 ;
        rml:objectMap [
            rml:template "http://example/{c2}"
        ]
    ] .

:firstJoinTM a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
            rml:quotedTriplesMap :elementaryTM1
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q1; ;
        rml:objectMap [
            rml:quotedTriplesMap :elementaryTM2
        ]
    ] .

:elementaryTM2 a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c3}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p2 ;
        rml:objectMap [
            rml:template "http://example/{c4}"
        ]
    ] .

:centralJoinTM a rml:AssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
            rml:quotedTriplesMap :firstJoinTM
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q2;
        rml:objectMap [
            rml:quotedTriplesMap :secondJoinTM
        ]
    ] .

:elementaryTM3 a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c5}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p3 ;
        rml:objectMap [
            rml:template "http://example/{c6}"
        ]
    ] .

:secondJoinTM a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
            rml:quotedTriplesMap :elementaryTM3
    ];
    rml:predicateObjectMap [
        rml:predicate ex:q3; ;
        rml:objectMap [
            rml:quotedTriplesMap :elementaryTM4
        ]
    ] .

:elementaryTM4 a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c7}"
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p4 ;
        rml:objectMap [
            rml:template "http://example/{c8}"
        ]
    ] .

```

**Output**
```
<<<<<http://example/s1><http://example/p1><http://example/o1>>><http://example/q1><<<http://example/s2><http://example/p2><http://example/o2>>>>><http://example/q2><<<<<http://example/s3><http://example/p3><http://example/o3>>><http://example/q3><<<http://example/s4><http://example/p4><http://example/o4>>>>>.
```

