package net.arctics.clonk.ui.search;

import java.util.concurrent.ExecutorService;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.IASTVisitor;
import net.arctics.clonk.ast.IEntityLocator;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.ast.TraversalContinuation;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.c4script.C4ScriptParser;
import net.arctics.clonk.c4script.Directive;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.ProblemReporter;
import net.arctics.clonk.c4script.ProblemReportingStrategy;
import net.arctics.clonk.c4script.ProblemReportingStrategy.Capabilities;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.ast.AccessDeclaration;
import net.arctics.clonk.c4script.ast.CallDeclaration;
import net.arctics.clonk.c4script.ast.IDLiteral;
import net.arctics.clonk.c4script.ast.MemberOperator;
import net.arctics.clonk.c4script.ast.StringLiteral;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Definition.ProxyVar;
import net.arctics.clonk.index.ID;
import net.arctics.clonk.ini.ComplexIniEntry;
import net.arctics.clonk.ini.FunctionEntry;
import net.arctics.clonk.ini.IDArray;
import net.arctics.clonk.ini.IniItem;
import net.arctics.clonk.ini.IniSection;
import net.arctics.clonk.ini.IniUnit;
import net.arctics.clonk.util.KeyValuePair;
import net.arctics.clonk.util.Sink;
import net.arctics.clonk.util.TaskExecution;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

public class ReferencesSearchQuery extends SearchQuery {

	protected Declaration declaration;
	private final Object[] scope;
	protected ProblemReportingStrategy strategy;

	public ReferencesSearchQuery(Declaration declaration, ClonkProjectNature project) {
		super();
		this.declaration = declaration.latestVersion();
		this.scope = declaration.occurenceScope(project);
	}

	@Override
	public String getLabel() {
		return String.format(Messages.ClonkSearchQuery_SearchFor, declaration.toString()); 
	}
	
	private class Visitor implements IResourceVisitor, IASTVisitor<ProblemReporter>, IEntityLocator {
		private ProblemReporter ctx;

		private boolean potentiallyReferencedByObjectCall(ASTNode expression) {
			if (expression instanceof CallDeclaration && expression.predecessorInSequence() instanceof MemberOperator) {
				final CallDeclaration callFunc = (CallDeclaration) expression;
				return callFunc.declaration() == null && callFunc.name().equals(declaration.name());
			}
			return false;
		}
		@Override
		public TraversalContinuation visitNode(ASTNode node, ProblemReporter context) {
			if (node instanceof AccessDeclaration) {
				final AccessDeclaration accessDeclExpr = (AccessDeclaration) node;
				Declaration dec = accessDeclExpr.declaration();
				if (dec != null)
					dec = dec.latestVersion();
				if (dec == declaration || (dec instanceof ProxyVar && ((ProxyVar)dec).definition() == declaration))
					result.addMatch(node, context, false, accessDeclExpr.indirectAccess());
				else if (
					dec instanceof Function && declaration instanceof Function &&
					((Function)dec).baseFunction() == ((Function)declaration).baseFunction()
				)
					result.addMatch(node, context, false, true);
				else if (potentiallyReferencedByObjectCall(node)) {
					final Function otherFunc = (Function) accessDeclExpr.declaration();
					final boolean potential = (otherFunc == null || !((Function)declaration).isRelatedFunction(otherFunc));
					result.addMatch(node, context, potential, accessDeclExpr.indirectAccess());
				}
			}
			else if (node instanceof IDLiteral && declaration instanceof Script) {
				if (((IDLiteral)node).definition() == declaration)
					result.addMatch(node, context, false, false);
			}
			else if (node instanceof StringLiteral) {
				final EntityRegion decRegion = node.entityAt(0, this);
				if (decRegion != null && decRegion.entityAs(Declaration.class) == declaration)
					result.addMatch(node, context, true, true);
			}
			return TraversalContinuation.Continue;
		}
		@Override
		public boolean visit(IResource resource) throws CoreException {
			if (resource instanceof IFile) {
				final Script script = Script.get(resource, true);
				if (script != null)
					searchScript(resource, script);
			}
			return true;
		}
		
		public void searchScript(IResource resource, Script script) {
			final C4ScriptParser parser = new C4ScriptParser(script);
			searchScript(resource, strategy.localReporter(parser.script(), parser.fragmentOffset(), null));
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <X> X context(Class<X> cls) {
			if (cls == ProblemReporter.class)
				return (X) ctx;
			else
				return null;
		}
		
		public void searchScript(IResource resource, ProblemReporter context) {
			ctx = context;
			final Script script = context.script();
			if (script.scriptFile() != null) {
				if (declaration instanceof Definition) {
					final Directive include = script.directiveIncludingDefinition((Definition) declaration);
					if (include != null)
						result.addMatch(include, context, false, false);
				}
				for (final Function f : script.functions())
					f.traverse(this, context);
			}

			// also search related files (actmap, defcore etc)
			try {
				searchScriptRelatedFiles(script);
			} catch (final CoreException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected IStatus doRun(IProgressMonitor monitor) throws OperationCanceledException {
		getSearchResult(); // make sure we have one
		final Visitor visitor = new Visitor();
		try {
			this.strategy = this.declaration.index().nature()
				.instantiateProblemReportingStrategies(Capabilities.TYPING).get(0);
		} catch (NullPointerException | IndexOutOfBoundsException e) {
			return Status.CANCEL_STATUS;
		}
		TaskExecution.threadPool(new Sink<ExecutorService>() {
			@Override
			public void receivedObject(ExecutorService pool) {
				for (final Object scope : ReferencesSearchQuery.this.scope)
					if (scope instanceof IContainer) try {
						((IContainer)scope).accept(visitor);
					} catch (final Exception e) {
						e.printStackTrace();
					}
					else if (scope instanceof Script) {
						final Script script = (Script) scope;
						pool.execute(new Runnable() {
							@Override
							public void run() {
								try {
									final C4ScriptParser parser = new C4ScriptParser(script);
									final ProblemReporter ctx = strategy.localReporter(parser.script(), parser.fragmentOffset(), null);
									visitor.searchScript((IResource) script.source(), ctx);
								} catch (final Exception e) {}
							}
						});
					}
					else if (scope instanceof Function) {
						final Function func = (Function)scope;
						final C4ScriptParser parser = new C4ScriptParser(func.script());
						func.traverse(visitor, strategy.localReporter(parser.script(), parser.fragmentOffset(), null));
					}
			}
		}, 20);
		return new Status(IStatus.OK, Core.PLUGIN_ID, 0, Messages.ClonkSearchQuery_Success, null);
	}

	private void searchScriptRelatedFiles(Script script) throws CoreException {
		if (script instanceof Definition) {
			final IContainer objectFolder = ((Definition)script).definitionFolder();
			for (final IResource res : objectFolder.members())
				if (res instanceof IFile) {
					final IFile file = (IFile)res;
					final Structure pinned = Structure.pinned(file, true, false);
					if (pinned instanceof IniUnit) {
						final IniUnit iniUnit = (IniUnit) pinned;
						for (final IniSection sec : iniUnit)
							for (final IniItem entry : sec)
								searchIniEntry(script, objectFolder, iniUnit, entry);
					}
				}
		}
	}

	private void searchIniEntry(Script script, final IContainer objectFolder, final IniUnit iniUnit, final IniItem entry) {
		if (entry instanceof ComplexIniEntry) {
			final ComplexIniEntry complex = (ComplexIniEntry) entry;
			if (complex.definition() != null) {
				final Class<?> entryClass = complex.definition().entryClass();
				if (entryClass == FunctionEntry.class) {
					final Definition obj = Definition.definitionCorrespondingToFolder(objectFolder);
					if (obj != null) {
						final Declaration declaration = obj.findFunction(complex.stringValue());
						if (declaration == this.declaration)
							result.addMatch(new SearchMatch(complex.toString(), 0, iniUnit, complex.end()-complex.stringValue().length(), complex.stringValue().length(), false, false));
					}
				}
				else if (declaration instanceof Definition)
					if (entryClass == ID.class) {
						if (script.index().anyDefinitionWithID((ID) complex.extendedValue()) == declaration)
							result.addMatch(new SearchMatch(complex.toString(), 0, iniUnit, complex.end()-complex.stringValue().length(), complex.stringValue().length(), false, false));
					}
					else if (entryClass == IDArray.class)
						for (final KeyValuePair<ID, Integer> pair : ((IDArray)complex.extendedValue()).components()) {
							final Definition obj = script.index().anyDefinitionWithID(pair.key());
							if (obj == declaration)
								result.addMatch(new SearchMatch(pair.toString(), 0, iniUnit, complex.end()-complex.stringValue().length(), complex.stringValue().length(), false, false));
						}
			}
		}
	}
	
	@Override
	public Match[] computeContainedMatches(AbstractTextSearchResult result, IEditorPart editor) {
		if (editor instanceof ITextEditor) {
			final Script script = Utilities.scriptForEditor(editor);
			if (script != null)
				return result.getMatches(script);
		}
		return NO_MATCHES;
	}

	@Override
	public boolean isShownInEditor(Match match, IEditorPart editor) {
		if (editor instanceof ITextEditor) {
			final Script script = Utilities.scriptForEditor(editor);
			if (script != null && match.getElement().equals(script.source()))
				return true;
		}
		return false;
	}

	@Override
	public Match[] computeContainedMatches(AbstractTextSearchResult result, IFile file) {
		final Script script = Script.get(file, true);
		if (script != null)
			return result.getMatches(script);
		return NO_MATCHES;
	}

}