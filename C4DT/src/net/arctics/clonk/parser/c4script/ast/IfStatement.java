package net.arctics.clonk.parser.c4script.ast;

import java.util.EnumSet;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.C4ScriptParser.TypeInfoList;
import net.arctics.clonk.parser.c4script.Keywords;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;

public class IfStatement extends ConditionalStatement {


	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private ExprElm elseExpr;

	public IfStatement(ExprElm condition, ExprElm body, ExprElm elseExpr) {
		super(condition, body);
		this.elseExpr = elseExpr;
		assignParentToSubElements();
	}

	@Override
	public String keyword() {
		return Keywords.If;
	}
	@Override
	public void doPrint(ExprWriter builder, int depth) {
		builder.append(keyword());
		builder.append(" ("); //$NON-NLS-1$
		condition.print(builder, depth);
		builder.append(")"); //$NON-NLS-1$
		printBody(builder, depth);
		if (elseExpr != null) {
			builder.append("\n"); //$NON-NLS-1$
			Conf.printIndent(builder, depth-1);
			builder.append(Keywords.Else);
			if (!(elseExpr instanceof IfStatement))
				printBody(elseExpr, builder, depth);
			else {
				builder.append(" ");
				elseExpr.print(builder, depth);
			}
		}
	}

	@Override
	public ExprElm[] subElements() {
		return new ExprElm[] {condition, body, elseExpr};
	}

	@Override
	public void setSubElements(ExprElm[] elms) {
		condition = elms[0];
		body      = elms[1];
		elseExpr  = elms[2];
	}
	
	@Override
	public EnumSet<ControlFlow> possibleControlFlows() {
		EnumSet<ControlFlow> result = EnumSet.of(ControlFlow.Continue);
		result.addAll(body.possibleControlFlows());
		if (elseExpr != null)
			result.addAll(elseExpr.possibleControlFlows());
		return result;
	}
	
	public ExprElm elseExpression() {
		return elseExpr;
	}
	
	@Override
	public ControlFlow controlFlow() {
		// return most optimistic flow (the smaller ordinal() the more "continuy" the flow is)
		ControlFlow ifCase = body.controlFlow();
		ControlFlow elseCase = elseExpr != null ? elseExpr.controlFlow() : ControlFlow.Continue;
		return ifCase.ordinal() < elseCase.ordinal() ? ifCase : elseCase;
	}
	
	@Override
	public Object evaluate(IEvaluationContext context) throws ControlFlowException {
		if (!condition.evaluate(context).equals(false))
			return body.evaluate(context);
		else if (elseExpr != null)
			return elseExpr.evaluate(context);
		else
			return null;
	}
	
	@Override
	public void reportErrors(C4ScriptParser parser) throws ParsingException {
		parser.reportErrorsOf(condition, true, null);
		// use two separate typeinfo lists for if and else statement, merging
		// gathered information afterwards
		TypeInfoList bodyTyping = new TypeInfoList(3);
		parser.reportErrorsOf(body, true, bodyTyping);
		if (elseExpr != null) {
			TypeInfoList elseTyping = new TypeInfoList(3);
			parser.reportErrorsOf(elseExpr, true, elseTyping);
			bodyTyping.inject(elseTyping);
		}
		parser.injectTypeInfos(bodyTyping);
	}

}