package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.Core;
import net.arctics.clonk.parser.ExprElm;

public class ExprElmWithSubElementsArray extends ExprElm {
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	protected ExprElm[] elements;
	public ExprElmWithSubElementsArray(ExprElm... elms) {
		this.elements = elms;
		assignParentToSubElements();
	}
	@Override
	public ExprElm[] subElements() {
		return elements;
	}
	@Override
	public void setSubElements(ExprElm[] elms) {
		elements = elms;
	}
	public ExprElm lastElement() {
		return elements != null && elements.length > 1 ? elements[elements.length-1] : null;
	}
}
