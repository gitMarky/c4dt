package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.c4script.Keywords;

public class BreakStatement extends KeywordStatement {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	@Override
	public String keyword() { return Keywords.Break; }
	@Override
	public ControlFlow controlFlow() { return ControlFlow.BreakLoop; }
}