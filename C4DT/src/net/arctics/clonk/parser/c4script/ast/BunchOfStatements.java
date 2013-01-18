package net.arctics.clonk.parser.c4script.ast;

import java.util.List;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.c4script.Conf;

public class BunchOfStatements extends Block {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	public BunchOfStatements(List<Statement> statements) {
		super(statements);
	}

	public BunchOfStatements(Statement... statements) {
		super(statements);
	}

	@Override
	public void doPrint(ASTNodePrinter builder, int depth) {
		boolean first = true;
		for (Statement statement : statements()) {
			statement.printPrependix(builder, depth);
			if (first)
				first = false;
			else {
				builder.append("\n"); //$NON-NLS-1$
				Conf.printIndent(builder, depth-1);
			}
			statement.print(builder, depth);
			statement.printAppendix(builder, depth);
		}
	}
}