package net.arctics.clonk.parser;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.resource.c4group.C4Entry;
import net.arctics.clonk.resource.c4group.C4GroupItem;

public class C4ScriptExtern extends C4ScriptBase {
	
	private static final long serialVersionUID = 1L;
	
	private SimpleScriptStorage scriptStorage;

	public C4ScriptExtern(C4GroupItem script) {
		setName(script.getName());
		scriptStorage = new SimpleScriptStorage((C4Entry) script);
	}
	
	@Override
	public ClonkIndex getIndex() {
		return ClonkCore.getDefault().EXTERN_INDEX;
	}

	@Override
	public Object getScriptFile() {
		return scriptStorage;
	}

}
