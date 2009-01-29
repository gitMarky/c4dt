package net.arctics.clonk.parser;

import java.io.Serializable;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.C4ScriptExprTree.ExprElm;

public class C4Directive extends C4Field implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public enum C4DirectiveType {
		STRICT,
		INCLUDE,
		APPENDTO;
		
		public static C4DirectiveType makeType(String arg) {
			if (arg.equals("strict")) return C4DirectiveType.STRICT;
			if (arg.equals("include")) return C4DirectiveType.INCLUDE;
			if (arg.equals("appendto")) return C4DirectiveType.APPENDTO;
			return null;
		}
		
		@Override
		public String toString() {
			return super.name().toLowerCase();
		}
	}
	
	private C4DirectiveType type;
	private String content;
	
	public C4Directive(C4DirectiveType type, String content) {
		this.content = content;
		this.type = type;
		switch (type) {
		case INCLUDE: case APPENDTO:
			if (content != null)
				this.content = content.substring(0, Math.min(4, content.length()));
		}
	}
	
	public C4Directive(String type, String content) {
		this(C4DirectiveType.makeType(type),content);
	}
	
	/**
	 * @return the type
	 */
	public C4DirectiveType getType() {
		return type;
	}
	/**
	 * @return the content
	 */
	public String getContent() {
		return content;
	}
	
	@Override
	public String toString() {
		if (content != "" && content != null)
			return "#" + type.toString() + " " + content;
		return "#" + type.toString();
	}
	
	public ExprElm getExprElm() {
		return new ExprElm() {
			@Override
			public int getExprStart() {
				return getLocation().getStart();
			}
			@Override
			public int getExprEnd() {
				return getLocation().getEnd();
			}
		};
	}
	
	public C4Object getIncludedObject(ClonkIndex index) {
		C4ID id = C4ID.getID(this.getContent());
		C4Object obj = index.getLastObjectWithId(id);
		if (obj == null)
			obj = ClonkCore.getDefault().EXTERN_INDEX.getLastObjectWithId(id);
		return obj;
	}
	
}
