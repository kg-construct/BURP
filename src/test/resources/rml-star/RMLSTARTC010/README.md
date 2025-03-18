## RMLSTARTC010

**Title**: 

**Description**: 

**Error expected?** Yes

**Input**
```
c1,c2,c3,c4
a,s,o,z
```

**Mapping**
```
@prefix rml: <http://w3id.org/rml/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix ex: <http://example/> .
@prefix : <http://example.org/> .
@base <http://example.org/> .

:firstTM a rml:AssertedTriplesMap, rml:NonAssertedTriplesMap ;
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
        rml:predicate ex:p;
        rml:quotedTriplesMap :secondTM ;
    ] .

:secondTM a rml:NonAssertedTriplesMap ;
    rml:logicalSource [
        rml:source [ a rml:Source, rml:RelativePathSource;
            rml:path "data.csv";
        ];
        rml:referenceFormulation rml:CSV
    ];
    rml:subjectMap [
        rml:template "http://example/{c2}" 
    ];
    rml:predicateObjectMap [
        rml:predicate ex:p ;
        rml:objectMap [
            rml:template "http://example/{c3}" 
        ]
    ] .

```

