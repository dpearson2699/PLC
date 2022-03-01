# PLC Semester Project

This	course	will	discuss	how	programming	languages	are	constructed	and	analyze	the	methodology	used	to	create	
existing	languages.		Topics	included	are:		lexical	analysis,	scanning,	programmatic	semantics,	code	generation,	and	
different	types	of	programming	languages.

## Parser Grammar
source ::= global* function*

global ::= ( list | mutable | immutable ) ';'
list ::= 'LIST' identifier '=' '[' expression (',' expression)* ']'
mutable ::= 'VAR' identifier ('=' expression)?
immutable ::= 'VAL' identifier '=' expression

function ::= 'FUN' identifier '(' (identifier (',' identifier)* )? ')' 'DO' block 'END'

block ::= statement*

statement ::=
    'LET' identifier ('=' expression)? ';' |
    'SWITCH' expression ('CASE' expression ':' block)* 'DEFAULT' block 'END' | 
    'IF' expression 'DO' block ('ELSE' block)? 'END' |
    'WHILE' expression 'DO' block 'END' |
    'RETURN' expression ';' |
    expression ('=' expression)? ';'

expression ::= logical_expression

logical_expression ::= comparison_expression (('&&' | '||') comparison_expression)*
comparison_expression ::= additive_expression (('<' | '>' | '==' | '!=') additive_expression)*
additive_expression ::= multiplicative_expression (('+' | '-') multiplicative_expression)*
multiplicative_expression ::= primary_expression (('*' | '/' | '^') primary_expression)*

primary_expression ::=
    'NIL' | 'TRUE' | 'FALSE' |
    integer | decimal | character | string |
    '(' expression ')' |
    identifier ('(' (expression (',' expression)*)? ')')? |
    identifier '[' expression ']'

identifier ::= '@'? [A-Za-z] [A-Za-z0-9_-]*
integer ::= '0' | '-'? [1-9] [0-9]*
decimal ::= '-'? ('0' | [1-9] [0-9]*) '.' [0-9]+
character ::= ['] ([^'\n\r\\] | escape) [']
string ::= '"' ([^"\n\r\\] | escape)* '"'
escape ::= '\' [bnrt'"\\]
operator ::= [!=] '='? | '&&' | '||' | 'any character'

whitespace ::= [ \b\n\r\t] 
