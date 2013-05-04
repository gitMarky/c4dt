package net.arctics.clonk.ini;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.arctics.clonk.Problem;
import net.arctics.clonk.ProblemException;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.IASTPositionProvider;
import net.arctics.clonk.c4group.C4GroupItem;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.parser.CStyleScanner;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.util.StreamUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class IniUnitParser extends CStyleScanner implements IASTPositionProvider {

	private final IniUnit unit;
	private IFile file;
	private final Markers markers = new Markers();

	public IniUnitParser(IniUnit iniUnit, Object source) {
		super(source);
		figureOutIndentation();
		unit = iniUnit;
		if (source instanceof IFile)
			file = (IFile) source;
	}

	@Override
	public void reset() {
		super.reset();
		if (file != null)
			this.reset(StreamUtil.stringFromFile(file));
	}

	protected IniSection parseSection(boolean modifyMarkers, IniSection parentSection) throws ProblemException {
		int targetIndentation = parentSection != null ? parentSection.indentation()+1 : 0;
		int rollback = tell();
		while (skipComment());
		// parse head
		int indentation = currentIndentation();
		int start = tell();
		if (read() == '[' && indentation == targetIndentation) {
			String name = readStringUntil(']', '\n', '\r');
			int end;
			if (read() != ']') {
				if (modifyMarkers)
					markers.marker(this, Problem.TokenExpected, unit, tell()-1, tell(), Markers.ABSOLUTE_MARKER_LOCATION|Markers.NO_THROW, IMarker.SEVERITY_ERROR, (Object)"]"); //$NON-NLS-1$
				return null;
			} else {
				if (!unit.isSectionNameValid(name, parentSection))
					if (modifyMarkers)
						markers.marker(this, Problem.UnknownSection, unit, start+1, tell()-1, Markers.ABSOLUTE_MARKER_LOCATION|Markers.NO_THROW, IMarker.SEVERITY_WARNING, name);
				end = tell();
				eat(BufferedScanner.NEWLINE_CHARS); // ignore rest of section line
			}
			IniSection section = unit.addSection(parentSection, start, name, end);
			section.setIndentation(indentation);
			// parse entries
			IniItem item= null;
			Map<String, IniItem> itemMap = new HashMap<String, IniItem>();
			List<IniItem> itemList = new LinkedList<IniItem>();
			section.setDefinition(unit.sectionDataFor(section, parentSection));
			while ((item = parseSectionOrEntry(section, modifyMarkers, section)) != null) {
				itemMap.put(item.key(),item);
				itemList.add(item);
			}
			// include single line ending following the entries so that editor region collapse management works properly
			skipSingleLineEnding();
			section.setSectionEnd(tell()+1);
			section.setSubItems(itemMap, itemList);
			return section;
		}
		else {
			seek(rollback);
			return null;
		}
	}

	protected boolean skipComment() {
		eatWhitespace();
		int _r;
		for (_r = read(); _r == 0; _r = read());
		char r = (char) _r;
		if (r == ';' || r == '#') {
			readStringUntil('\n');
			eatWhitespace();
			return true;
		}
		else if (r == '/')
			switch (read()) {
			case '/':
				eatUntil(BufferedScanner.NEWLINE_CHARS);
				eatWhitespace();
				return true;
			case '*':
				for (;!reachedEOF();)
					if (read() == '/')
						break;
					else
						unread();
				eatWhitespace();
				return true;
			default:
				unread(); unread();
				return false;
			}
		else {
			unread();
			return false;
		}
	}

	public final synchronized void parse(boolean modifyMarkers) throws ProblemException {
		parse(modifyMarkers, true);
	}

	public synchronized void parse(boolean modifyMarkers, boolean resetScannerWithFileContents) throws ProblemException {
		unit.startParsing();
		try {
			if (resetScannerWithFileContents)
				reset();
			if (modifyMarkers && unit.file() != null)
				try {
					unit.file().deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ONE);
					// deactivate creating markers if it's contained in a linked group
					modifyMarkers = C4GroupItem.groupItemBackingResource(unit.file()) == null;
				} catch (CoreException e) {
					e.printStackTrace();
				}
			unit.clear();
			seek(0);
			IniSection section;
			while ((section = parseSection(modifyMarkers, null)) != null) {
				unit.sectionsMap.put(section.name(), section);
				unit.sectionsList.add(section);
			}
		} finally {
			unit.endParsing();
		}
	}

	protected IniEntry parseEntry(IniSection section, boolean modifyMarkers, IniSection parentSection) throws ProblemException {
		int targetIndentation = parentSection != null ? parentSection.indentation() : 0;
		int rollback = tell();
		while (skipComment());
		eatWhitespace();
		int indentation = currentIndentation();
		if (indentation != targetIndentation || read() == '[') {
			seek(rollback);
			return null;
		}
		unread();
		if (reachedEOF()) {
			seek(rollback);
			return null;
		}
		int keyStart = tell();
		String key = readIdent();
		eatWhitespace();
		if (read() != '=')
			if (modifyMarkers)
				markers.marker(this, Problem.TokenExpected, unit, keyStart+key.length(), tell(), Markers.NO_THROW|Markers.ABSOLUTE_MARKER_LOCATION, IMarker.SEVERITY_ERROR, (Object)"="); //$NON-NLS-1$
		eat(BufferedScanner.WHITESPACE_WITHOUT_NEWLINE_CHARS);
		String value = readStringUntil(BufferedScanner.NEWLINE_CHARS);
		int valEnd = tell();
		int commentStart = value != null ? value.indexOf('#') : -1;
		if (commentStart != -1) {
			valEnd -= value.length()-commentStart;
			value = value.substring(0, commentStart);
		}
		//eat(BufferedScanner.NEWLINE_CHARS);
		IniEntry entry = new IniEntry(keyStart, valEnd, key, value);
		entry.setParent(section);
		try {
			return unit.validateEntry(entry, section, modifyMarkers);
		} catch (IniParserException e) {
			if (modifyMarkers)
				markers.marker(this, Problem.GenericError, unit, e.offset(), e.endOffset(), Markers.NO_THROW|Markers.ABSOLUTE_MARKER_LOCATION, e.severity(), (Object)e.getMessage());
			return entry;
		}
	}

	protected IniItem parseSectionOrEntry(IniSection section, boolean modifyMarkers, IniSection parentSection) throws ProblemException {
		IniEntry entry = parseEntry(section, modifyMarkers, parentSection);
		if (entry != null)
			return entry;
		IniSection sec = parseSection(modifyMarkers, parentSection);
		if (sec != null)
			return sec;

		return null;
	}

	public static String category(IniField annot, Class<?> cls) {
		if (annot.category().equals(""))
			return defaultSection(cls);
		else
			return annot.category();
	}

	public static String defaultSection(Class<?> cls) {
		IniDefaultSection defSec = cls.getAnnotation(IniDefaultSection.class);
		return defSec != null ? defSec.name() : IniDefaultSection.DEFAULT;
	}

	@Override
	public IFile file() { return file; }
	@Override
	public Declaration container() { return unit; }
	@Override
	public int fragmentOffset() { return 0; }

}