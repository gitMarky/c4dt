package net.arctics.clonk.ui.editors.c4script;

import net.arctics.clonk.resource.ClonkProjectNature;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

/**
 * Planned edit strategies:
 * <ul>
 * <li>smart indent</li>
 * <li><tt>}</tt> insertion after <tt>{</tt>+<tt>enter</tt></li>
 * <li>?automatic closing of <tt>)</tt>? this needs some really good intelligence</li>
 * <li>instead of <tt>\t</tt> always insert two spaces</li>
 * <li>complete parameter insertion when defining an object callback func(e.g. Contained* funcs)</li>
 * </ul>
 * @author ZokRadonh
 *
 */
public class C4ScriptAutoIndentStrategy extends DefaultIndentLineAutoEditStrategy {
	public C4ScriptAutoIndentStrategy() {
	}
	
	public C4ScriptAutoIndentStrategy(ClonkProjectNature project, String partitioning) {
	}

	/*
	 * Facts:
	 * 1. backspaces are only customizable if attached to viewers key-listener (see jdt)
	 * 2. search methods(scan* in jdt) should always respect partitioning to avoid strings and comments
	 * 3. every insertion has to check if the characters already exist before adding them to c.text
	 * 
	 * Unclear points:
	 * 1. sense/use of ReplaceEdit/DeleteEdit/...
	 * 2. correct calculation of c.caretOffset
	 */
	
	
	@Override
	public void customizeDocumentCommand(IDocument d, DocumentCommand c) {
		if (c.text.contains("\n") || c.text.contains("\r")) { //$NON-NLS-1$ //$NON-NLS-2$
			try {
				//String originalText = c.text;
				IRegion reg = d.getLineInformationOfOffset(c.offset);
				String line = d.get(reg.getOffset(),reg.getLength());
				int count = countIndentOfLine(line);
				line = line.trim();
				if (line.endsWith("{")) { //$NON-NLS-1$
					count++;
				}
				for(int i = 0; i < count; i++) c.text += "  "; //$NON-NLS-1$
				if (line.endsWith("{")) { //$NON-NLS-1$
					c.text += "\r\n"; //$NON-NLS-1$
					for(int i = 0; i < count - 1; i++) c.text += "\t"; //$NON-NLS-1$
					c.text += "}"; //$NON-NLS-1$
					//c.caretOffset = c.offset+1;
//					c.caretOffset = c.offset + count * 2 + originalText.length();
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
//		super.customizeDocumentCommand(d, c);
	}
	
	private int countIndentOfLine(String line) {
		int indentStep = 2;
		int whitespace = 0;
		int indent = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\t') {
				indent++;
				i++;
			}
			else if (c == ' ')
				whitespace++;
			else
				break;
		}
		return indent + whitespace/indentStep;
	}
	
	
}
