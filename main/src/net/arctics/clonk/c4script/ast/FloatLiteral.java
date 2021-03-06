package net.arctics.clonk.c4script.ast;

import net.arctics.clonk.Core;

public class FloatLiteral extends NumberLiteral {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	private final double literal;
	
	public FloatLiteral(final double literal) {
		this.literal = literal;
	}
	
	@Override
	public Number literal() {
		return literal;
	}
	
	@Override
	public boolean literalsEqual(final Literal<?> other) {
		return other instanceof FloatLiteral ? ((FloatLiteral)other).literal == this.literal : super.literalsEqual(other);
	}

}
