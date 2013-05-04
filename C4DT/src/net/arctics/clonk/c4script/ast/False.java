package net.arctics.clonk.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.c4script.Keywords;

public class False extends BoolLiteral {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	@Override
	public boolean booleanValue() {
		return false;
	}
	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		output.append(Keywords.False);
	}
	@Override
	public Boolean literal() {
		return false;
	}
}
