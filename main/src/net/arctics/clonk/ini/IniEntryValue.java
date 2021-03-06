package net.arctics.clonk.ini;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.IASTSection;
import net.arctics.clonk.ini.IniData.IniEntryDefinition;

public abstract class IniEntryValue extends ASTNode implements IASTSection {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	public void setInput(final String value, final IniEntryDefinition entryData, final IniUnit context) throws IniParserException {}
	public Object value(final Object context) { return toString(); }
	public boolean isEmpty() { return false; }
	@Override
	public int absoluteOffset() { return sectionOffset()+start(); }
}