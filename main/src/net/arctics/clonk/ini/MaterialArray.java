package net.arctics.clonk.ini;

import net.arctics.clonk.Core;
import net.arctics.clonk.util.KeyValuePair;

public class MaterialArray extends ArrayValue<String, Integer> {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	@Override
	public KeyValuePair<String, Integer> singleComponentFromString(final int offset, final String s) {
		final String[] split = s.split("="); //$NON-NLS-1$
		if (split.length == 2)
			return new KeyValuePair<String, Integer>(split[0], Integer.valueOf(split[1]));
		else
			return new KeyValuePair<String, Integer>(s, 1); 
	}

	public String name(final int index) { return components().get(index).key(); }
	public int count(final int index) { return components().get(index).value(); }

}
