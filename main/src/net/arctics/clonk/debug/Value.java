package net.arctics.clonk.debug;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

import net.arctics.clonk.c4script.Keywords;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.util.Utilities;

public class Value extends DebugElement implements IValue {

	private Object value;
	private PrimitiveType type;

	public Value(final Target target, final Object value) {
		super(target);
		setValue(value, PrimitiveType.correspondingToInstance(value));
	}

	public Object getValue() {
		return value;
	}

	public PrimitiveType getType() {
		return type;
	}

	public void setValue(final Object value, final PrimitiveType type) {
		this.value = value;
		this.type = type;
	}

	@Override
	public String getReferenceTypeName() throws DebugException {
		return null;
	}

	@Override
	public String getValueString() throws DebugException {
		return value != null ? value.toString() : Keywords.Nil;
	}

	@Override
	public IVariable[] getVariables() throws DebugException {
		return NO_VARIABLES;
	}

	@Override
	public boolean hasVariables() throws DebugException {
		return false;
	}

	@Override
	public boolean isAllocated() throws DebugException {
		return true;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Value) {
			final Value other = (Value) obj;
			return Utilities.eq(other.value, value);
		}
		return false;
	}

}