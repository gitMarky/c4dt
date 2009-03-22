package net.arctics.clonk.parser.defcore;

import java.io.InputStream;
import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.C4ID;
import net.arctics.clonk.parser.inireader.ComplexIniEntry;
import net.arctics.clonk.parser.inireader.IniEntry;
import net.arctics.clonk.parser.inireader.IniReader;
import net.arctics.clonk.parser.inireader.IniData.IniConfiguration;
import org.eclipse.core.resources.IFile;

public class DefCoreParser extends IniReader {
	
//	private final static String[] DEFCORE_SECTIONS = new String[] { "DefCore" , "Physical" };
//	
//	private final List<DefCoreOption> defCoreOptions = DefCoreOption.createNewDefCoreList();
//	private final List<DefCoreOption> physicalOptions = DefCoreOption.createNewPhysicalList();
	
	private final IniConfiguration configuration = ClonkCore.getDefault().INI_CONFIGURATIONS.getConfigurationFor("DefCore.txt");
	
	public DefCoreParser(InputStream stream) {
		super(stream);
	}
	
	public DefCoreParser(IFile file) {
		super(file);
	}
	
	@Override
	public IniConfiguration getConfiguration() {
		return configuration;
	}

//	/**
//	 * Searches the option for given name
//	 * @param name the name of the option (e.g. "BurnTo")
//	 * @return The found option or <tt>null</tt> if not found
//	 */
//	public DefCoreOption getDefCoreOption(String name) {
//		ListIterator<DefCoreOption> it = defCoreOptions.listIterator();
//		while(it.hasNext()) {
//			if (it.next().getName().equalsIgnoreCase(name)) return it.previous();
//		}
//		return null;
//	}
//	
//	/**
//	 * Searches the option for given name
//	 * @param name the name of the option (e.g. "Throw")
//	 * @return The found option or <tt>null</tt> if not found
//	 */
//	public DefCoreOption getPhysicalOption(String name) {
//		ListIterator<DefCoreOption> it = physicalOptions.listIterator();
//		while(it.hasNext()) {
//			if (it.next().getName().equalsIgnoreCase(name)) return it.previous();
//		}
//		return null;
//	}
//	

	public C4ID getObjectID() {
		IniEntry entry = entryInSection("DefCore", "id");
		if (entry instanceof ComplexIniEntry)
			return (C4ID)((ComplexIniEntry)entry).getExtendedValue();
		return C4ID.NULL;
	}
	
	public String getName() {
		IniEntry entry = entryInSection("DefCore", "Name");
		return entry instanceof ComplexIniEntry ? (String)((ComplexIniEntry)entry).getExtendedValue() : defaultName;
	}
	
}
