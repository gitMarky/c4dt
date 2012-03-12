package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.c4script.Keywords;
import net.arctics.clonk.parser.c4script.ProplistDeclaration;

/**
 * new <protoype> { ... } expression as syntactic sugar for { Prototype = <prototype>, ... }
 * @author madeen
 *
 */
public class NewProplist extends PropListExpression {
	
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	/**
	 * Create new NewProplist expression. The passed {@link ProplistDeclaration} will get its {@link ProplistDeclaration#implicitPrototype()} set to the {@code prototypeExpression} parameter.
	 * @param declaration The declaration representing the proplist block
	 * @param prototypeExpression The prototype expression
	 */
	public NewProplist(ProplistDeclaration declaration, ExprElm prototypeExpression) {
		super(declaration.withImplicitProtoype(prototypeExpression));
	}
	
	@Override
	public void doPrint(ExprWriter output, int depth) {
		output.append(Keywords.New);
		output.append(" ");
		definedDeclaration().implicitPrototype().print(output, depth);
		output.append(" ");
		super.doPrint(output, depth);
	}
	
	@Override
	public ExprElm[] subElements() {
		ExprElm[] result = new ExprElm[1+components().size()];
		result[0] = definedDeclaration().implicitPrototype();
		for (int i = 0; i < components().size(); i++)
			result[1+i] = components().get(i).initializationExpression();
		return result;
	}

}