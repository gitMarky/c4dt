package net.arctics.clonk.parser.foldermap;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.inireader.IniUnit;

public class FolderMapUnit extends IniUnit {

	@Override
	protected String configurationName() {
		return "FolderMap.txt";
	}
	
	public FolderMapUnit(Object input) {
		super(input);
	}

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

}
