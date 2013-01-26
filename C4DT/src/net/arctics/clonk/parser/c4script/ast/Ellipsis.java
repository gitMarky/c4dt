package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.ASTNodePrinter;
import net.arctics.clonk.parser.c4script.C4ScriptParser;

public class Ellipsis extends ASTNode {


	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	public Ellipsis() {
		super();
	}

	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		output.append("..."); //$NON-NLS-1$
	}

	@Override
	public boolean isValidAtEndOfSequence(C4ScriptParser context) {
		return false;
	}

}