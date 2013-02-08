package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.ASTNodePrinter;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.Operator;

public class CombinedMatchingPlaceholder extends MatchingPlaceholder {
	private static final long serialVersionUID = 1L;
	private final MatchingPlaceholder left, right;
	private final Operator operator;

	public CombinedMatchingPlaceholder(MatchingPlaceholder left, MatchingPlaceholder right, Operator operator) throws ParsingException {
		super("");
		this.left = left;
		this.right = right;
		this.operator = operator;
		if (left.entryName().equals(right.entryName()))
			this.entryName = left.entryName();
	}

	@Override
	public boolean satisfiedBy(ASTNode element) {
		switch (operator) {
		case And: case BitAnd:
			return left.satisfiedBy(element) && right.satisfiedBy(element);
		case Or: case BitOr:
			return left.satisfiedBy(element) || right.satisfiedBy(element);
		default:
			return false;
		}
	}

	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		left.print(output, depth);
		output.append(' ');
		output.append(operator.operatorName());
		output.append(' ');
		right.print(output, depth);
	}
}
