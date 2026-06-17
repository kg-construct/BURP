/*
 [The "BSD licence"]
 Copyright (c) 2024, Jakub Duchateau (@ Université de Liège, https://www.uliege.be/)
 Copyright (c) 2014, Alejandro Medrano (@ Universidad Politecnica de Madrid, http://www.upm.es/)
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
*/

// Derived from https://www.w3.org/TR/rdf12-turtle/#sec-grammar-grammar


// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

grammar Turtle;

// [1] turtleDoc ::= statement*
turtleDoc
    : statement* EOF
    ;

// [2] statement ::= directive | (triples '.')
statement
    : directive
    | triples '.'
    ;

// [3] directive ::= prefixID | base | sparqlPrefix | sparqlBase
directive
    : prefixID
    | base
    | sparqlPrefix
    | sparqlBase
    ;

// [4] prefixID ::= '@prefix' PNAME_NS IRIREF '.'
prefixID
    : '@prefix' PNAME_NS IRIREF '.'
    ;

// [5] base ::= '@base' IRIREF '.'
base
    : '@base' IRIREF '.'
    ;

// [6] sparqlPrefix ::= 'PREFIX' PNAME_NS IRIREF
sparqlPrefix
    : 'PREFIX' PNAME_NS IRIREF
    ;

// [7] sparqlBase ::= 'BASE' IRIREF
sparqlBase
    : 'BASE' IRIREF
    ;

// [8] triples ::= (subject predicateObjectList) | (blankNodePropertyList predicateObjectList?) | (reifiedTriple predicateObjectList?)
triples
    : subject predicateObjectList
    | blankNodePropertyList predicateObjectList?
    | reifiedTriple predicateObjectList?
    ;

// [9] predicateObjectList ::= verb objectList (';' (verb objectList)?)*
predicateObjectList
    : verb objectList (';' (verb objectList)?)*
    ;

// [10] objectList ::= object annotation (',' object annotation)*
objectList
    : object_ annotation (',' object_ annotation)*
    ;

// [11] verb ::= predicate | 'a'
verb
    : iri // from [13] predicate ::= iri
    | 'a'
    ;

// [12] subject ::= iri | BlankNode | collection
subject
    : iri
    | BlankNode
    | collection
    ;

// [14] object ::= iri | BlankNode | collection | blankNodePropertyList | literal | tripleTerm | reifiedTriple
object_
    : iri
    | BlankNode
    | collection
    | blankNodePropertyList
    | literal
    | tripleTerm
    | reifiedTriple
    ;

// [15] literal ::= RDFLiteral | NumericLiteral | BooleanLiteral
literal
    : rdfLiteral
    | NumericLiteral
    | BooleanLiteral
    ;

// [16] blankNodePropertyList ::= '[' predicateObjectList ']'
blankNodePropertyList
    : '[' predicateObjectList ']'
    ;

// [17] collection ::= '(' object* ')'
collection
    : '(' object_* ')'
    ;

// [18] NumericLiteral ::= INTEGER | DECIMAL | DOUBLE
NumericLiteral
    : INTEGER
    | DECIMAL
    | DOUBLE
    ;

// [19] RDFLiteral ::= String (LANGTAG | '^^' iri)?
rdfLiteral
    : string (LANG_DIR | '^^' iri)?
    ;

// [20] BooleanLiteral ::= 'true' | 'false'
BooleanLiteral
    : 'true'
    | 'false'
    ;

// [21] String ::= STRING_LITERAL_QUOTE | STRING_LITERAL_SINGLE_QUOTE | STRING_LITERAL_LONG_SINGLE_QUOTE | STRING_LITERAL_LONG_QUOTE
string
    : STRING_LITERAL_QUOTE
    | STRING_LITERAL_SINGLE_QUOTE
    | STRING_LITERAL_LONG_SINGLE_QUOTE
    | STRING_LITERAL_LONG_QUOTE
    ;

// [22] iri ::= IRIREF | PrefixedName
iri
    : IRIREF
    | PrefixedName
    ;

// [24] BlankNode ::= BLANK_NODE_LABEL | ANON
BlankNode
    : BLANK_NODE_LABEL
    | ANON
    ;

// Turtle-Star

// [25] reifier ::= '~' (iri | BlankNode)?
reifier
    : '~' (iri | BlankNode)?
    ;

// [26] reifiedTriple ::= '<<' rtSubject verb rtObject reifier? '>>'
reifiedTriple
    : '<<' rtSubject verb rtObject reifier? '>>'
    ;

// [27] rtSubject ::= iri | BlankNode | reifiedTriple
rtSubject
    : iri
    | BlankNode
    | reifiedTriple
    ;

// [28] rtObject ::= iri | BlankNode | literal | tripleTerm | reifiedTriple
rtObject
    : iri
    | BlankNode
    | literal
    | tripleTerm
    | reifiedTriple
    ;

// [29] tripleTerm ::= '<<(' ttSubject verb ttObject ')>>'
tripleTerm
    : '<<(' ttSubject verb ttObject ')>>'
    ;

// [30] ttSubject ::= iri | BlankNode
ttSubject
    : iri
    | BlankNode
    ;

// [31] ttObject ::= iri | BlankNode | literal | tripleTerm
ttObject
    : iri
    | BlankNode
    | literal
    | tripleTerm
    ;

// [32] annotation ::= (reifier | annotationBlock)*
annotation
    : (reifier | annotationBlock)*
    ;

// [33] annotationBlock ::= '{|' predicateObjectList '|}'
annotationBlock
    : '{|' predicateObjectList '|}'
    ;

// 
// Productions for terminals
//

// [35] IRIREF ::= '<' ([^#x00-#x20<>"{}|^`\] | UCHAR)* '>'
IRIREF
    : '<' (~[\u0000-\u0020<>"{}|^`\\] | UCHAR)* '>'
    ;

// [36] PNAME_NS ::= PN_PREFIX? ':'
PNAME_NS
    : PN_PREFIX? ':'
    ;

// [23] PrefixedName ::= PNAME_LN | PNAME_NS
PrefixedName
    : PNAME_LN
    | PNAME_NS
    ;

// [37] PNAME_LN ::= PNAME_NS PN_LOCAL
PNAME_LN
    : PNAME_NS PN_LOCAL
    ;

// [50] WS ::= #x20 | #x9 | #xD | #xA
WS
    : ([\t\u000C] | ' ')+ -> skip
    ;
NL
    : ('\r'?'\n') -> channel(HIDDEN)
    ;

// [55] PN_PREFIX ::= PN_CHARS_BASE ((PN_CHARS | '.')* PN_CHARS)?
PN_PREFIX
    : PN_CHARS_BASE ((PN_CHARS | '.')* PN_CHARS)?
    ;

// [38] BLANK_NODE_LABEL ::= '_:' (PN_CHARS_U | [0-9]) ((PN_CHARS | '.')* PN_CHARS)?
BLANK_NODE_LABEL
    : '_:' (PN_CHARS_U | [0-9]) ((PN_CHARS | '.')* PN_CHARS)?
    ;

// [39] LANG_DIR ::= '@' [a-zA-Z]+ ('-' [a-zA-Z0-9]+)* ('--' [a-zA-Z]+)?
LANG_DIR
    : '@' [a-zA-Z]+ ('-' [a-zA-Z0-9]+)* ('--' [a-zA-Z]+)?
    ;

// [40] INTEGER ::= [+-]? [0-9]+
INTEGER
    : [+-]? [0-9]+
    ;

// [41] DECIMAL ::= [+-]? [0-9]* '.' [0-9]+
DECIMAL
    : [+-]? [0-9]* '.' [0-9]+
    ;

// [42] DOUBLE ::= [+-]? ([0-9]+ '.' [0-9]* EXPONENT | '.' [0-9]+ EXPONENT | [0-9]+ EXPONENT)
DOUBLE
    : [+-]? ([0-9]+ '.' [0-9]* EXPONENT | '.' [0-9]+ EXPONENT | [0-9]+ EXPONENT)
    ;

// [43] EXPONENT ::= [eE] [+-]? [0-9]+
EXPONENT
    : [eE] [+-]? [0-9]+
    ;

// [46] STRING_LITERAL_LONG_SINGLE_QUOTE ::= "'''" (("'" | "''")? ([^'\] | ECHAR | UCHAR))* "'''"
STRING_LITERAL_LONG_SINGLE_QUOTE
    : '\'\'\'' (('\'' | '\'\'')? ((~ ['\\]) | ECHAR | UCHAR))* '\'\'\''
    ;

// [47] STRING_LITERAL_LONG_QUOTE ::=  	'"""' (('"' | '""')? ([^"\] | ECHAR | UCHAR))* '"""'
STRING_LITERAL_LONG_QUOTE
    : '"""' (('\'' | '\'\'')? ((~ ['\\]) | ECHAR | UCHAR))* '"""'
    ;

// [44] STRING_LITERAL_QUOTE ::= '"' ([^#x22#x5C#x0A#x0D] | ECHAR | UCHAR)* '"'
STRING_LITERAL_QUOTE
    : '"' ((~ [\u0022\u005C\u000A\u000D]) | ECHAR | UCHAR)* '"'
    ;

// [45] STRING_LITERAL_SINGLE_QUOTE ::= "'" ([^#x27#x5C#x0A#x0D] | ECHAR | UCHAR)* "'"
STRING_LITERAL_SINGLE_QUOTE
    : '\'' ((~ [\u0027\u005C\u000A\u000D]) | ECHAR | UCHAR)* '\''
    ;

// [48] UCHAR ::= '\u' HEX HEX HEX HEX | '\U' HEX HEX HEX HEX HEX HEX HEX HEX
UCHAR
    : '\\u' HEX HEX HEX HEX
    | '\\U' HEX HEX HEX HEX HEX HEX HEX HEX
    ;

// [49] ECHAR ::= '\' [tbnrf\"']
ECHAR
    : '\\' [tbnrf"'\\]
    ;

// [51] ANON ::= '[' WS* ']'
ANON_WS
    : ' '
    | '\t'
    | '\r'
    | '\n'
    ;

ANON
    : '[' ANON_WS* ']'
    ;

// [52] PN_CHARS_BASE ::= ...
PN_CHARS_BASE
    : 'A' .. 'Z'
    | 'a' .. 'z'
    | '\u00C0' .. '\u00D6'
    | '\u00D8' .. '\u00F6'
    | '\u00F8' .. '\u02FF'
    | '\u0370' .. '\u037D'
    | '\u037F' .. '\u1FFF'
    | '\u200C' .. '\u200D'
    | '\u2070' .. '\u218F'
    | '\u2C00' .. '\u2FEF'
    | '\u3001' .. '\uD7FF'
    | '\uF900' .. '\uFDCF'
    | '\uFDF0' .. '\uFFFD'
    ;

// [53] PN_CHARS_U ::= PN_CHARS_BASE | '_'
PN_CHARS_U
    : PN_CHARS_BASE
    | '_'
    ;

// [54] PN_CHARS ::= PN_CHARS_U | '-' | [0-9] | #xB7 | [#x0300-#x036F] | [#x203F-#x2040]
PN_CHARS
    : PN_CHARS_U
    | '-'
    | [0-9]
    | '\u00B7'
    | [\u0300-\u036F]
    | [\u203F-\u2040]
    ;

// [56] PN_LOCAL ::= (PN_CHARS_U | ':' | [0-9] | PLX) ((PN_CHARS | '.' | ':' | PLX)* (PN_CHARS | ':' | PLX))?
PN_LOCAL
    : (PN_CHARS_U | ':' | [0-9] | PLX) ((PN_CHARS | '.' | ':' | PLX)* (PN_CHARS | ':' | PLX))?
    ;

// [57] PLX ::= PERCENT | PN_LOCAL_ESC
PLX
    : PERCENT
    | PN_LOCAL_ESC
    ;

// [58] PERCENT ::= '%' HEX HEX
PERCENT
    : '%' HEX HEX
    ;

// [59] HEX ::= [0-9] | [A-F] | [a-f]
HEX
    : [0-9]
    | [A-F]
    | [a-f]
    ;

// [60] PN_LOCAL_ESC ::= '\' ('_' | '~' | '.' | '-' | "!" | '$' | '&' | "'" | '(' | ')' | '*' | '+' | ',' | ';' | '=' | '/' | '?' | '#' | '@' | '%')
PN_LOCAL_ESC
    : '\\' (
        '_'
        | '~'
        | '.'
        | '-'
        | '!'
        | '$'
        | '&'
        | '\''
        | '('
        | ')'
        | '*'
        | '+'
        | ','
        | ';'
        | '='
        | '/'
        | '?'
        | '#'
        | '@'
        | '%'
    )
    ;

LC
    : '#' ~[\r\n]* -> channel(HIDDEN)
    ;