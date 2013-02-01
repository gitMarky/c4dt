package net.arctics.clonk.parser.c4script.ast;

import java.util.LinkedList;
import java.util.List;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.ASTNodePrinter;
import net.arctics.clonk.parser.c4script.Conf;
import net.arctics.clonk.parser.c4script.Operator;
import net.arctics.clonk.parser.c4script.ProblemReportingContext;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;

public class BinaryOp extends OperatorExpression {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	@Override
	public ASTNode optimize(final ProblemReportingContext context) throws CloneNotSupportedException {
		// #strict 2: ne -> !=, S= -> ==
		if (context.script().strictLevel() >= 2) {
			Operator op = operator();
			if (op == Operator.StringEqual || op == Operator.eq)
				op = Operator.Equal;
			else if (op == Operator.ne)
				op = Operator.NotEqual;
			if (op != operator())
				return new BinaryOp(op, leftSide().optimize(context), rightSide().optimize(context));
		}

		// blub() && blab() && return(1); -> {blub(); blab(); return(1);}
		if ((operator() == Operator.And || operator() == Operator.Or) && (parent() instanceof SimpleStatement)) {// && getRightSide().isReturn()) {
			ASTNode block = convertOperatorHackToBlock(context);
			if (block != null)
				return block;
		}

		return super.optimize(context);
	}

	private ASTNode convertOperatorHackToBlock(ProblemReportingContext context) throws CloneNotSupportedException {
		LinkedList<ASTNode> leftSideArguments = new LinkedList<ASTNode>();
		ASTNode r;
		boolean works = true;
		Operator hackOp = this.operator();
		// gather left sides (must not be operators)
		for (r = leftSide(); r instanceof BinaryOp; r = ((BinaryOp)r).leftSide()) {
			BinaryOp op = (BinaryOp)r;
			if (op.operator() != hackOp) {
				works = false;
				break;
			}
			if (op.rightSide() instanceof BinaryOp) {
				works = false;
				break;
			}
			leftSideArguments.addLast(op.rightSide());
		}
		// return at the right end signals this should rather be a block
		if (works) {
			leftSideArguments.addFirst(r);
			List<ASTNode> statements = new LinkedList<ASTNode>();
			// wrap expressions in statements
			for (ASTNode ex : leftSideArguments)
				statements.add(new SimpleStatement(ex.optimize(context)));
			// convert func call to proper return statement
			if (rightSide().controlFlow() == ControlFlow.Return)
				statements.add(new ReturnStatement(((CallDeclaration)rightSide()).soleParm().optimize(context)));
			else
				statements.add(new SimpleStatement(rightSide().optimize(context)));
			return new Block(statements);
		}
		return null;
	}

	private ASTNode leftSide, rightSide;

	@Override
	public ASTNode[] subElements() {
		return new ASTNode[] {leftSide, rightSide};
	}

	@Override
	public void setSubElements(ASTNode[] elements) {
		leftSide  = elements[0];
		rightSide = elements[1];
	}

	public BinaryOp(Operator operator, ASTNode leftSide, ASTNode rightSide) {
		super(operator);
		setLeftSide(leftSide);
		setRightSide(rightSide);
	}

	public BinaryOp(Operator op) {
		super(op);
	}

	public ASTNode leftSide() {
		return leftSide;
	}

	public ASTNode rightSide() {
		return rightSide;
	}

	public void setLeftSide(ASTNode leftSide) {
		this.leftSide = leftSide;
		leftSide.setParent(this);
	}

	public void setRightSide(ASTNode rightSide) {
		this.rightSide = rightSide;
		rightSide.setParent(this);
	}

	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		// put brackets around operands in case some transformation messed up prioritization
		boolean needsBrackets = leftSide instanceof BinaryOp && operator().priority() > ((BinaryOp)leftSide).operator().priority();
		if (needsBrackets)
			output.append("("); //$NON-NLS-1$
		leftSide.print(output, depth);
		if (needsBrackets)
			output.append(")"); //$NON-NLS-1$

		output.append(" "); //$NON-NLS-1$
		output.append(operator().operatorName());
		output.append(" "); //$NON-NLS-1$

		needsBrackets = rightSide instanceof BinaryOp && operator().priority() > ((BinaryOp)rightSide).operator().priority();
		if (needsBrackets)
			output.append("("); //$NON-NLS-1$
		if (rightSide instanceof PropListExpression)
			Conf.blockPrelude(output, depth);
		rightSide.print(output, depth);
		if (needsBrackets)
			output.append(")"); //$NON-NLS-1$
	}


	@Override
	public Object evaluateAtParseTime(IEvaluationContext context) {
		try {
			Object leftSide  = operator().firstArgType().convert(this.leftSide().evaluateAtParseTime(context));
			Object rightSide = operator().secondArgType().convert(this.rightSide().evaluateAtParseTime(context));
			if (leftSide != null && leftSide != ASTNode.EVALUATION_COMPLEX) {
				switch (operator()) {
				case And:
					// false && <anything> => false
					if (leftSide.equals(false))
						return false;
					break;
				case Or:
					// true || <anything> => true
					if (leftSide.equals(true))
						return true;
					break;
				default:
					break;
				}
				if (rightSide != null && rightSide != ASTNode.EVALUATION_COMPLEX)
					return evaluateOn(leftSide, rightSide);
			}
		}
		catch (ClassCastException e) {}
		catch (NullPointerException e) {}
		return super.evaluateAtParseTime(context);
	}

	private Object evaluateOn(Object leftSide, Object rightSide) {
        switch (operator()) {
        case Add:
        	return ((Number)leftSide).doubleValue() + ((Number)rightSide).doubleValue();
        case Subtract:
        	return ((Number)leftSide).doubleValue() - ((Number)rightSide).doubleValue();
        case Multiply:
        	return ((Number)leftSide).doubleValue() * ((Number)rightSide).doubleValue();
        case Divide:
        	return ((Number)leftSide).doubleValue() / ((Number)rightSide).doubleValue();
        case Modulo:
        	return ((Number)leftSide).doubleValue() % ((Number)rightSide).doubleValue();
        case Larger:
        	return ((Number)leftSide).doubleValue() > ((Number)rightSide).doubleValue();
        case Smaller:
        	return ((Number)leftSide).doubleValue() < ((Number)rightSide).doubleValue();
        case LargerEqual:
        	return ((Number)leftSide).doubleValue() >= ((Number)rightSide).doubleValue();
        case SmallerEqual:
        	return ((Number)leftSide).doubleValue() <= ((Number)rightSide).doubleValue();
        case StringEqual:
        case Equal:
        	return leftSide.equals(rightSide);
        default:
        	return null;
        }
    }

	@Override
	public Object evaluate(IEvaluationContext context) throws ControlFlowException {
	    Object left = leftSide().evaluate(context);
	    Object right = rightSide().evaluate(context);
	    if (left != null && right != null)
	    	return evaluateOn(left, right);
	    else
	    	return null;
	}

	@Override
	public boolean isConstant() {
		// CNAT_Left | CNAT_Right are considered constant for example
		switch (operator()) {
		case BitOr: case BitAnd:
			return leftSide().isConstant() && rightSide().isConstant();
		default:
			return false;
		}
	}

}