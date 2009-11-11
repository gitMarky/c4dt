package net.arctics.clonk.ui.editors.c4script;

import java.util.LinkedList;
import java.util.List;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.ExternIndex;
import net.arctics.clonk.index.IExternalScript;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.DeclarationRegion;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.AccessDeclaration;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.IExpressionListener;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.MemberOperator;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.TraversalContinuation;
import net.arctics.clonk.util.IPredicate;
import net.arctics.clonk.util.Utilities;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Little helper thingie to find declarations
 *
 */
public class DeclarationLocator extends ExpressionLocator {
	private ITextEditor editor;
	private String line;
	private C4Declaration declaration;
	private List<C4Declaration> proposedDeclarations;
	
	public List<C4Declaration> getProposedDeclarations() {
		return proposedDeclarations;
	}

	public ITextEditor getEditor() {
		return editor;
	}

	private static IPredicate<C4Declaration> isFunc = new IPredicate<C4Declaration>() {
		public boolean test(C4Declaration item) {
			return item instanceof C4Function;
		}
	};
	
	public DeclarationLocator(ITextEditor editor, IDocument doc, IRegion region) throws BadLocationException, ParsingException {
		this.editor = editor;
		final C4ScriptBase script = Utilities.getScriptForEditor(getEditor());
		if (script == null)
			return;
		C4Function func = script.funcAt(region);
		if (func == null) {
			// outside function, fallback to old technique
			simpleFindDeclaration(doc, region, script, null);
			return;
		}
		int bodyStart = func.getBody().getOffset();
		if (region.getOffset() >= bodyStart) {
			exprRegion = new Region(region.getOffset()-bodyStart,0);
			C4ScriptParser parser = C4ScriptParser.reportExpressionsAndStatements(doc, func.getBody(), script, func, this);
			if (exprAtRegion != null) {
				DeclarationRegion declRegion = exprAtRegion.declarationAt(exprRegion.getOffset()-exprAtRegion.getExprStart(), parser);
				boolean setRegion;
				if (
					declRegion != null && declRegion.getDeclaration() != null &&
					(!(exprAtRegion.getPredecessorInSequence() instanceof MemberOperator) || !declRegion.getDeclaration().isGlobal())
				) {
					this.declaration = declRegion.getDeclaration();
					setRegion = true;
				}
				else if (exprAtRegion instanceof AccessDeclaration) {
					AccessDeclaration access = (AccessDeclaration) exprAtRegion;
					List<C4Declaration> projectDeclarations = script.getIndex() instanceof ExternIndex ? null : script.getIndex().getDeclarationMap().get(access.getDeclarationName());
					List<C4Declaration> externalDeclarations = ClonkCore.getDefault().getExternIndex().getDeclarationMap().get(access.getDeclarationName());
					
					IPredicate<C4Declaration> isFuncAndInDeps = new IPredicate<C4Declaration>() {
						@Override
						public boolean test(C4Declaration item) {
							return item instanceof C4Function && script.getIndex().acceptsFromExternalLib(((IExternalScript)item.getScript()).getExternalLib());
						}
					};
					if (projectDeclarations != null)
						projectDeclarations = Utilities.filter(projectDeclarations, isFunc);
					if (externalDeclarations != null)
						externalDeclarations = Utilities.filter(externalDeclarations, isFuncAndInDeps);
					C4Function engineFunc = ClonkCore.getDefault().getActiveEngine().findFunction(access.getDeclarationName());
					if (projectDeclarations != null || externalDeclarations != null || engineFunc != null) {
						proposedDeclarations = new LinkedList<C4Declaration>();
						if (projectDeclarations != null)
							proposedDeclarations.addAll(projectDeclarations);
						if (externalDeclarations != null)
							proposedDeclarations.addAll(externalDeclarations);
						if (engineFunc != null)
							proposedDeclarations.add(engineFunc);
						if (proposedDeclarations.size() == 0)
							proposedDeclarations = null;
						else if (proposedDeclarations.size() == 1) {
							this.declaration = proposedDeclarations.get(0);
						}
						setRegion = proposedDeclarations != null;
					}
					else
						setRegion = false;
				}
				else
					setRegion = false;
				if (setRegion)
					this.exprRegion = new Region(bodyStart+declRegion.getRegion().getOffset(), declRegion.getRegion().getLength());
			}
		}
		else
			simpleFindDeclaration(doc, region, script, func);
	}

	private void simpleFindDeclaration(IDocument doc, IRegion region,
			C4ScriptBase script, C4Function func) throws BadLocationException {
		IRegion lineInfo;
		String line;
		try {
			lineInfo = doc.getLineInformationOfOffset(region.getOffset());
			line = doc.get(lineInfo.getOffset(),lineInfo.getLength());
		} catch (BadLocationException e) {
			return;
		}
		int localOffset = region.getOffset() - lineInfo.getOffset();
		int start,end;
		for (start = localOffset; start > 0 && Character.isJavaIdentifierPart(line.charAt(start-1)); start--);
		for (end = localOffset; end < line.length() && Character.isJavaIdentifierPart(line.charAt(end)); end++);
		exprRegion = new Region(lineInfo.getOffset()+start,end-start);
		declaration = script.findDeclaration(doc.get(exprRegion.getOffset(), exprRegion.getLength()), new FindDeclarationInfo(script.getIndex(), func));
	}
	
	/**
	 * @return the line
	 */
	public String getLine() {
		return line;
	}

	/**
	 * @return the identRegion
	 */
	public IRegion getIdentRegion() {
		return exprRegion;
	}

	/**
	 * @return the declaration
	 */
	public C4Declaration getDeclaration() {
		return declaration;
	}

	public TraversalContinuation expressionDetected(ExprElm expression, C4ScriptParser parser) {
		expression.traverse(new IExpressionListener() {
			public TraversalContinuation expressionDetected(ExprElm expression, C4ScriptParser parser) {
				if (exprRegion.getOffset() >= expression.getExprStart() && exprRegion.getOffset() < expression.getExprEnd()) {
					exprAtRegion = expression;
					return TraversalContinuation.TraverseSubElements;
				}
				return TraversalContinuation.Continue;
			}
		}, parser);
		return exprAtRegion != null ? TraversalContinuation.Cancel : TraversalContinuation.Continue;
	}
}