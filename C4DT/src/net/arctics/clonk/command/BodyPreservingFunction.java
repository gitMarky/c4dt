package net.arctics.clonk.command;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.ast.Block;
import net.arctics.clonk.parser.c4script.ast.ControlFlowException;
import net.arctics.clonk.parser.c4script.ast.ReturnException;
import net.arctics.clonk.parser.c4script.ast.Statement;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;
import net.arctics.clonk.parser.c4script.ast.evaluate.IVariableValueProvider;

public class BodyPreservingFunction extends C4Function {
	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	private transient Block body;
	
	public void setBodyBlock(Block body) {
		this.body = body;
	}

	public Block getBodyBlock() {
		return body;
	}

	@Override
	public Object invoke(final Object... args) {
		final IVariableValueProvider variableProvider = args != null && args.length > 0 && args[0] instanceof IVariableValueProvider ? (IVariableValueProvider)args[0] : null;
		IEvaluationContext context = new IEvaluationContext() {

			@Override
			public Object[] getArguments() {
				return args;
			}

			@Override
			public C4Function getFunction() {
				return BodyPreservingFunction.this;
			}

			@Override
			public Object getValueForVariable(String varName) {
				return variableProvider != null ? variableProvider.getValueForVariable(varName) : null;
			}

		};
		for (Statement s : body.getStatements()) {
			try {
				s.evaluate(context);
			} catch (ReturnException e) {
				return e.getResult();
			} catch (ControlFlowException e) {
				switch (e.getControlFlow()) {
				case BreakLoop:
					return null;
				case Continue:
					break;
				default:
					return null;
				}
			}
		}
		return null;
	}
}