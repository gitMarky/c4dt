package net.arctics.clonk.c4script.typing.dabble;

import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.ast.AccessVar;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.c4script.typing.TypeVariable;

public class VariableTypeVariable extends TypeVariable {
	private final Variable variable;
	public Variable variable() { return variable; }
	public VariableTypeVariable(final Variable variable) {
		this.variable = variable;
		this.type = PrimitiveType.UNKNOWN;
	}
	public VariableTypeVariable(final AccessVar origin) { this((Variable) origin.declaration()); }
	@Override
	public void apply(final boolean soft) {
		variable.assignType(type);
	}
	@Override
	public String toString() { return String.format("[%s: %s]", variable.name(), get().typeName(true)); }
	@Override
	public Declaration declaration() { return variable; }
	@Override
	public Declaration key() { return variable; }
}