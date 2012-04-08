package net.arctics.clonk.parser.inireader;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.inireader.IniData.IniDataSection;

public class ScenarioUnit extends IniUnit {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	
	@Override
	protected String configurationName() {
		return "Scenario.txt"; //$NON-NLS-1$
	}
	
	public ScenarioUnit(Object input) {
		super(input);
	}
	
	@Override
	protected IniDataSection sectionDataFor(IniSection section, IniSection parentSection) {
		if (section.name().startsWith("Player")) //$NON-NLS-1$
			return configuration().getSections().get("Player"); //$NON-NLS-1$
		return super.sectionDataFor(section, parentSection);
	}
	
	@Override
	protected boolean isSectionNameValid(String name, IniSection parentSection) {
		return (parentSection == null && name.matches("Player[1234]")) || super.isSectionNameValid(name, parentSection); //$NON-NLS-1$
	}

}
