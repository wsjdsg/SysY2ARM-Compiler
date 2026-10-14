grammar Sysy;

parse : compunit+ ;

compunit : decl|funcdef;

LINE_COMMENT : '//' .*? '\r'?'\n' -> skip;
COMMENT : '/*' .*? '*/' -> skip;
BLANK : [ \t\r\n]+ -> skip;

INT : 'int';
FLOAT : 'float';
VOID: 'void';
CONST : 'const';
IF : 'if';
ELSE : 'else';
WHILE : 'while';
BREAK : 'break';
CONTINUE : 'continue';
RETURN : 'return';
IDENT : [A-Za-z_][A-Za-z0-9_]*;

number : intconst | floatconst;
intconst : DEC_INT | OCT_INT | HEX_INT;
floatconst : DEC_FLOAT | HEX_FLOAT;
DEC_INT : '0' | ([1-9][0-9]*);
OCT_INT : '0'[0-7]+;
HEX_INT : '0'('x'|'X')[0-9A-Fa-f]+;
DEC_FLOAT : [0-9]*'.'[0-9]*(('p'|'P'|'e'|'E')('+'|'-')?[0-9]+)?
            | [0-9]+[.]?[0-9]*('p'|'P'|'e'|'E')(('+'|'-')?[0-9]+)?;
HEX_FLOAT : '0'('x'|'X')[0-9A-Fa-f]*'.'[0-9A-Fa-f]*(('p'|'P'|'e'|'E')('+'|'-')?[0-9A-Fa-f]*)?
            | '0'('x'|'X')[0-9A-Fa-f]*[.]?[0-9A-Fa-f]*('p'|'P'|'e'|'E')(('+'|'-')?[0-9A-Fa-f]*)?;


LOGIC_AND : '&&';
LOGIC_OR : '||';
EQ : '==';
NEQ : '!=';
LT : '<';
GT : '>';
LE : '<=';
GE : '>=';
ADD : '+';
SUB : '-';
MUL : '*';
DIV : '/';
MOD : '%';
NOT : '!';
ASSIGN : '=';

SEMI : ';';
COMMA : ',';
LPARENT : '(';
RPARENT : ')';
LBRACK : '[';
RBRACK : ']';
LBRACE : '{';
RBRACE : '}';
STR : '"' (ESC |.)*? '"';
fragment ESC : '\\"' | '\\\\';

btype : INT | FLOAT ;
decl : (CONST)? btype def ( COMMA def )* SEMI ;
def : IDENT ( LBRACK exp RBRACK )* (ASSIGN initval)? ;
/*
constdecl : CONST btype constdef ( COMMA constdef )* SEMI ;
constdef : IDENT ( LBRACK exp RBRACK )* ASSIGN initval ;
vardecl : btype vardef ( COMMA vardef )* SEMI ;
vardef : (IDENT ( LBRACK exp RBRACK )*) | (IDENT ( LBRACK exp RBRACK )* ASSIGN initval) ;
*/
initval : exp | initarray ;
initarray : LBRACE ( initval ( COMMA initval )* )? RBRACE ;
funcdef : functype IDENT LPARENT (funcfparams)? RPARENT block;
functype : VOID | INT | FLOAT;
funcfparam : btype IDENT (LBRACK RBRACK ( LBRACK exp RBRACK )*)?;
funcfparams : funcfparam ( COMMA funcfparam )* ;
block : LBRACE ( blockitem )* RBRACE;
blockitem : decl | stmt;
stmt : assign | expstmt | block | ifstmt | whilestmt | breakstmt | continuestmt | returnstmt ;
assign : lval ASSIGN exp SEMI;
expstmt : (exp)? SEMI;
ifstmt : IF LPARENT cond RPARENT stmt ( ELSE stmt )? ;
whilestmt : WHILE LPARENT cond RPARENT stmt;
breakstmt : BREAK SEMI;
continuestmt : CONTINUE SEMI;
returnstmt : RETURN (exp)? SEMI ;
exp : binaryexp | unaryexp;
cond : lorexp;
lval : IDENT (LBRACK exp RBRACK)*;
primaryexp : LPARENT exp RPARENT | lval | number | funccall;
unaryexp : (unaryop)* primaryexp;
funccall : IDENT LPARENT (funcrparams)? RPARENT;
unaryop : ADD | SUB | NOT ;
funcrparams : (STR | exp) ( COMMA (STR | exp) )*;
binaryexp : unaryexp (op unaryexp)* ;
op : MUL | DIV | MOD | ADD | SUB | LT | GT
    | LE | GE | EQ | NEQ;
lorexp : landexp (LOGIC_OR landexp)*;
landexp : binaryexp (LOGIC_AND binaryexp)*;
/*
mulexp : unaryexp | unaryexp (MUL | DIV | MOD) mulexp;
addexp : mulexp | mulexp (ADD | SUB) addexp;
relexp : addexp | addexp (LT | GT | LE | GE) relexp;
eqexp : relexp | relexp (EQ | NEQ) eqexp;
landexp : eqexp | eqexp LOGIC_AND landexp;
lorexp : landexp | landexp LOGIC_OR lorexp;
*/
/*
mulexp : unaryexp ((MUL | DIV | MOD) unaryexp)*;
addexp : mulexp ((ADD | SUB) mulexp)*;
relexp : addexp ((LT | GT | LE | GE) addexp)*;
eqexp : relexp ((EQ | NEQ) relexp)*;
landexp : eqexp (LOGIC_AND eqexp)*;
lorexp : landexp (LOGIC_OR landexp)*;
*/
