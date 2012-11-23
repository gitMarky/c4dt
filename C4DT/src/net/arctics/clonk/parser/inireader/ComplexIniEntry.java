package net.arctics.clonk.parser.inireader;

import java.util.Collection;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.inireader.IniData.IniEntryDefinition;
import net.arctics.clonk.util.IHasChildren;
import net.arctics.clonk.util.IHasChildrenWithContext;
import net.arctics.clonk.util.IHasContext;
import net.arctics.clonk.util.INode;
import net.arctics.clonk.util.ITreeNode;

public class ComplexIniEntry extends IniEntry implements IHasChildren, IHasContext  {
	
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	private Object extendedValue;
	private IniEntryDefinition entryConfig;

	protected ComplexIniEntry(int pos, int endPos, String key, String value) {
		super(pos,endPos, key,value);
	}

	public ComplexIniEntry(int pos, int endPos, String key, Object value) {
		super(pos,endPos, key,null);
		extendedValue = value;
	}
	
	public Object extendedValue() {
		return extendedValue;
	}
	
	public void setEntryConfig(IniEntryDefinition entryConfig) {
		this.entryConfig = entryConfig;
	}
	
	public IniEntryDefinition entryConfig() {
		return entryConfig;
	}
	
	public static ComplexIniEntry adaptFrom(IniEntry entry, Object extendedValue, IniEntryDefinition config, boolean createErrorMarkers) {
		ComplexIniEntry cmpl = new ComplexIniEntry(entry.start(), entry.end(), entry.key(), entry.stringValue());
		cmpl.entryConfig = config;
		cmpl.extendedValue = extendedValue;
		cmpl.setParentDeclaration(entry.parentDeclaration());
		return cmpl;
	}
	
	public IniUnit iniUnit() {
		return this.topLevelParentDeclarationOfType(IniUnit.class);
	}
	
	@Override
	public String stringValue() {
		return extendedValue.toString();
	}
	
	@Override
	public Object value() {
		return extendedValue;
	}
	
	@Override
	public void setStringValue(String value, Object context) {
		if (extendedValue instanceof IIniEntryValue)
			try {
				((IIniEntryValue)extendedValue).setInput(value, entryConfig, (IniUnit) context);
			} catch (IniParserException e) {
				e.printStackTrace();
			}
		else if (extendedValue instanceof String)
			extendedValue = value;
	}

	@Override
	public Object[] children() {
		if (extendedValue instanceof IHasChildrenWithContext)
			return ((IHasChildrenWithContext)extendedValue).children(this);
		else if (extendedValue instanceof IHasChildren)
			return ((IHasChildren)extendedValue).children();
		return null;
	}

	@Override
	public Collection<? extends INode> childCollection() {
		if (extendedValue instanceof ITreeNode)
			return ((ITreeNode) extendedValue).childCollection();
		return null;
	}
	
	@Override
	public boolean hasChildren() {
		return
			(extendedValue instanceof IHasChildren && ((IHasChildren)extendedValue).hasChildren()) ||
			(extendedValue instanceof IHasChildrenWithContext && ((IHasChildrenWithContext)extendedValue).hasChildren());
	}

	@Override
	public Object context() {
		return this; // is it's own context; over-abstraction is awesome -.-
	}
	
	@Override
	public void validate() {
		if (extendedValue() instanceof IComplainingIniEntryValue)
			((IComplainingIniEntryValue)extendedValue()).complain(this);
	}
	
	@Override
	public boolean isTransient() {
		return value() instanceof IniEntryValueBase && ((IniEntryValueBase)value()).isEmpty();
	}
	
}
