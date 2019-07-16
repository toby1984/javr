lexer grammar AvrLexer;

OPERATORS : [+\-\*/] ;
EOL: [\n] ;
IDENTIFIER : [_a-zA-Z]+[_0-9a-z]* ;
WS  : [ \t\r]+ ;