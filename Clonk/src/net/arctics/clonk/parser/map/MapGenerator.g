grammar MapGenerator;

@header {
package net.arctics.clonk.parser.map;
}

@lexer::header {package net.arctics.clonk.parser.map;}

@members {
C4MapCreator mapCreator;
C4MapOverlay current;
C4MapOverlay lastOverlay;

public MapGeneratorParser(C4MapCreator mapCreator, TokenStream input) {
	this(input);
	this.mapCreator = mapCreator;
	this.current = mapCreator;
}

void createMapObject(String type, String name) {
	try {
		C4MapOverlay newOverlay = current.createOverlay(type, name);
		current = newOverlay;
	} catch (Exception e) {
		e.printStackTrace();
	}
}


void createMapObject(Class<? extends C4MapOverlay> cls, String name) {
	try {
		C4MapOverlay newOverlay = current.createOverlay(cls, name);
		current = newOverlay;
	} catch (Exception e) {
		e.printStackTrace();
	}
}

private void setVal(String name, String value) {
	try {
		current.setAttribute(name, value);
	} catch (Exception e) {
		e.printStackTrace();
	}
}

private void moveLevelUp() {
	lastOverlay = current;
	current = (C4MapOverlay) current.getParentDeclaration();
}

private void assignOperator(String t) {
	C4MapOverlay.Operator op = C4MapOverlay.Operator.valueOf(t.charAt(0));
	lastOverlay.setOperator(op);
}

}

parse	:	statement*;

statement
	:	{lastOverlay = null;} composition STATEMENTEND;

composition
	:	subobject (op=OPERATOR {assignOperator($op.text);} composition)?;

subobject
	:	MAP name=NAME? {createMapObject(C4Map.class, $name.text);} block
	|	OVERLAY name=NAME? {createMapObject(C4MapOverlay.class, $name.text);} block
	|	template=NAME name=NAME? {createMapObject($template.text, $name.text);} block;

block	:	BLOCKOPEN statementorattrib* BLOCKCLOSE {moveLevelUp();};

statementorattrib
	:	attribute|statement;

attribute
	:	attr=NAME ASSIGN attrValue=NAME STATEMENTEND {setVal($attr.text, $attrValue.text);}
	|	attr=NAME ASSIGN attrValue=NUMBER STATEMENTEND {setVal($attr.text, $attrValue.text);}
	|	attr=NAME ASSIGN attrValue=MATCOMBO STATEMENTEND {setVal($attr.text, $attrValue.text);};

MAP		:	'map';
OVERLAY		:	'overlay';

fragment LETTER	:	'a'..'z'|'A'..'Z'|'_';
fragment DIGIT	:	'0'..'9';
fragment INT		:	('+'|'-')? DIGIT+;
fragment WORD	:	LETTER (LETTER|DIGIT)*;

NUMBER		:	INT;
NAME		:	WORD;
MATCOMBO	:	WORD '-' WORD;
WS		:	(' '|'\t'|'\n'|'\r')+ {skip();};
SLCOMMENT	:	'//' .* '\n' {skip();};
MLCOMMENT	:	'/*' .* '*/' {skip();};
ASSIGN		:	'=';
BLOCKOPEN	:	'{';
BLOCKCLOSE	:	'}';
STATEMENTEND	:	';';
OPERATOR		:	'|'|'&'|'^';
