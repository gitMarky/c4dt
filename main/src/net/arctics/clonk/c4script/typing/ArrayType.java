package net.arctics.clonk.c4script.typing;

import static java.lang.String.format;
import static net.arctics.clonk.util.ArrayUtil.iterable;
import static net.arctics.clonk.util.Utilities.defaulting;

import java.util.Iterator;

import net.arctics.clonk.Core;
import net.arctics.clonk.util.Utilities;

/**
 * Array type where either general element type or the types for specific elements is known.
 * @author madeen
 *
 */
public class ArrayType implements IRefinedPrimitiveType {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private final IType elementType;
	/**
	 * Construct a new ArrayType.
	 * @param elmType The element type. When providing specific element types, this parameter should be null
	 * @param knownTypesForSpecificElements Specific types for elements. The index of the type in this array corresponds to the index in the array instances of this type.
	 */
	public ArrayType(final IType elementType) { this.elementType = elementType; }
	/**
	 * Get the general element type. If the general element type is not set a type set consisting of the specific element types will be returned.
	 * @return
	 */
	public IType elementType() { return elementType; }
	@Override
	public Iterator<IType> iterator() { return iterable(PrimitiveType.ARRAY, this).iterator(); }
	@Override
	public IType simpleType() { return PrimitiveType.ARRAY; }
	@Override
	public String toString() { return typeName(true); }
	@Override
	public PrimitiveType primitiveType() { return PrimitiveType.ARRAY; }
	/**
	 * The type name of an array type will either describe the type in terms of its general element type or the specific element types.
	 */
	@Override
	public String typeName(final boolean special) {
		final String generalArray = PrimitiveType.ARRAY.typeName(false);
		return !special
			? generalArray
			: format("%s[%s]", generalArray, defaulting(elementType(), PrimitiveType.ANY).typeName(special));
	}
	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof ArrayType) {
			final ArrayType otherArrType = (ArrayType) obj;
			return Utilities.eq(this.elementType, otherArrType.elementType);
		} else
			return false;
	}
}
