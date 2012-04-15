package net.arctics.clonk.parser.c4script;

import static net.arctics.clonk.util.ArrayUtil.arrayIterable;

import java.util.Iterator;

import net.arctics.clonk.Core;

public class NillableType implements IType {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	private IType baseType; 
	
	public static IType make(IType baseType) {
		return baseType instanceof NillableType ? baseType : new NillableType(baseType);
	}
	
	private NillableType(IType baseType) {
		this.baseType = baseType;
	}
	
	@Override
	public Iterator<IType> iterator() {
		return arrayIterable(baseType, PrimitiveType.ANY).iterator(); 
	}

	@Override
	public boolean canBeAssignedFrom(IType other) {
		return PrimitiveType.ANY.canBeAssignedFrom(other);
	}

	@Override
	public String typeName(boolean special) {
		return baseType.typeName(special) + "?";
	}

	@Override
	public boolean intersects(IType type) {
		return baseType.intersects(type) || PrimitiveType.ANY.intersects(type);
	}

	@Override
	public boolean subsetOf(IType type) {
		return baseType.subsetOf(type);
	}

	@Override
	public boolean subsetOfAny(IType... types) {
		return baseType.subsetOfAny(types);
	}

	@Override
	public int specificness() {
		return baseType.specificness();
	}

	@Override
	public IType staticType() {
		return baseType.staticType();
	}

	@Override
	public void setTypeDescription(String description) {
		baseType.setTypeDescription(description);
	}
	
	@Override
	public IType eat(IType other) {return this;}

}