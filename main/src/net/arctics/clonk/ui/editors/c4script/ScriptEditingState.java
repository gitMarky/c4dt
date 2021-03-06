package net.arctics.clonk.ui.editors.c4script;

import static net.arctics.clonk.util.Utilities.as;
import static net.arctics.clonk.util.Utilities.attempt;
import static net.arctics.clonk.util.Utilities.voidResult;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import net.arctics.clonk.Core;
import net.arctics.clonk.Problem;
import net.arctics.clonk.ProblemException;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.DeclMask;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.ExpressionLocator;
import net.arctics.clonk.ast.IASTPositionProvider;
import net.arctics.clonk.ast.IASTVisitor;
import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.ast.TraversalContinuation;
import net.arctics.clonk.builder.ClonkProjectNature;
import net.arctics.clonk.c4group.C4GroupItem;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.Function.FunctionScope;
import net.arctics.clonk.c4script.FunctionFragmentParser;
import net.arctics.clonk.c4script.ProblemReporter;
import net.arctics.clonk.c4script.ProblemReportingStrategy;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.ScriptParser;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.c4script.ast.AccessDeclaration;
import net.arctics.clonk.c4script.ast.AccessVar;
import net.arctics.clonk.c4script.ast.BinaryOp;
import net.arctics.clonk.c4script.ast.Block;
import net.arctics.clonk.c4script.ast.CallDeclaration;
import net.arctics.clonk.c4script.ast.Comment;
import net.arctics.clonk.c4script.ast.EntityLocator;
import net.arctics.clonk.c4script.ast.FunctionBody;
import net.arctics.clonk.c4script.ast.IFunctionCall;
import net.arctics.clonk.c4script.ast.Literal;
import net.arctics.clonk.c4script.ast.PropListExpression;
import net.arctics.clonk.c4script.typing.FunctionType;
import net.arctics.clonk.c4script.typing.IType;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.parser.CStyleScanner;
import net.arctics.clonk.parser.IMarkerListener;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.ui.editors.CStylePartitionScanner;
import net.arctics.clonk.ui.editors.DeclarationProposal;
import net.arctics.clonk.ui.editors.EntityHyperlink;
import net.arctics.clonk.ui.editors.ScriptCommentScanner;
import net.arctics.clonk.ui.editors.StructureEditingState;
import net.arctics.clonk.ui.editors.StructureTextScanner.ScannerPerEngine;
import net.arctics.clonk.util.Pair;
import net.arctics.clonk.util.StringUtil;
import net.arctics.clonk.util.Utilities;

/**
 * C4Script-specific specialization of {@link StructureEditingState} that tries to only trigger a full reparse of the script when necessary (i.e. not when editing inside of a function)
 * @author madeen
 *
 */
public final class ScriptEditingState extends StructureEditingState<C4ScriptEditor, Script> implements IASTPositionProvider {


	public final class Assistant extends ContentAssistant {
		private ScriptCompletionProcessor processor;
		public Assistant() {
			processor = new ScriptCompletionProcessor(ScriptEditingState.this);
			for (final String s : CStylePartitionScanner.PARTITIONS) {
				setContentAssistProcessor(processor, s);
			}
			setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
			setRepeatedInvocationMode(true);
			setStatusLineVisible(true);
			setStatusMessage(Messages.C4ScriptSourceViewerConfiguration_StandardProposals);
			enablePrefixCompletion(false);
			enableAutoInsert(true);
			enableAutoActivation(true);
			setAutoActivationDelay(0);
			enableColoredLabels(true);
			setInformationControlCreator(parent -> {
				final DefaultInformationControl def = new DefaultInformationControl(parent,Messages.C4ScriptSourceViewerConfiguration_PressTabOrClick);
				return def;
			});
			//setSorter(processor);
			addCompletionListener(processor);
		}
		// make these public
		@Override
		public void hide() { super.hide(); }
		@Override
		public boolean isProposalPopupActive() { return super.isProposalPopupActive(); }
		public ScriptCompletionProcessor processor() { return processor; }
		public void showParameters(final ITextOperationTarget target) {
			if (target.canDoOperation(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION)) {
				target.doOperation(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);
			}
		}
	}

	class ScriptHyperlinkDetector implements IHyperlinkDetector {
		@Override
		public IHyperlink[] detectHyperlinks(final ITextViewer viewer, final IRegion region, final boolean canShowMultipleHyperlinks) {
			try {
				final EntityLocator locator = new EntityLocator(structure(), viewer.getDocument(), region);
				if (locator.entity() != null) {
					return new IHyperlink[] {
						new EntityHyperlink(locator.expressionRegion(), locator.entity())
					};
				} else if (locator.potentialEntities() != null) {
					return new IHyperlink[] {
						new EntityHyperlink(locator.expressionRegion(), locator.potentialEntities())
					};
				}
				return null;
			} catch (final Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}


	public class ScriptTextHover extends ClonkTextHover {
		private EntityLocator entityLocator;
		@Override
		public String getHoverInfo(final ITextViewer viewer, final IRegion region) {
			final IFile scriptFile = structure().file();
			final StringBuilder messageBuilder = new StringBuilder();
			appendEntityInfo(viewer, region, messageBuilder);
			appendMarkerInfo(region, scriptFile, messageBuilder);
			return messageBuilder.toString();
		}
		private void appendEntityInfo(final ITextViewer viewer, final IRegion region, final StringBuilder messageBuilder) {
			if (entityLocator != null && entityLocator.entity() != null) {
				messageBuilder.append(entityLocator.infoText());
			} else {
				final String superInfo = super.getHoverInfo(viewer, region);
				if (superInfo != null) {
					messageBuilder.append(superInfo);
				}
			}
		}
		private void appendMarkerInfo(final IRegion region, final IFile scriptFile, final StringBuilder messageBuilder) {
			try {
				final IMarker[] markers = scriptFile.findMarkers(Core.MARKER_C4SCRIPT_ERROR, true, IResource.DEPTH_ONE);
				boolean foundSomeMarkers = false;
				for (final IMarker m : markers) {
					int charStart;
					final IRegion markerRegion = new Region(
						charStart = m.getAttribute(IMarker.CHAR_START, -1),
						m.getAttribute(IMarker.CHAR_END, -1)-charStart
					);
					if (Utilities.regionContainsOtherRegion(markerRegion, region)) {
						if (!foundSomeMarkers) {
							if (messageBuilder.length() > 0)
							 {
								messageBuilder.append("<br/><br/><b>"+Messages.C4ScriptTextHover_Markers1+"</b><br/>"); //$NON-NLS-1$
							}
							foundSomeMarkers = true;
						}
						String msg = m.getAttribute(IMarker.MESSAGE).toString();
						msg = StringUtil.htmlerize(msg);
						messageBuilder.append(msg);
						messageBuilder.append("<br/>"); //$NON-NLS-1$
					}
				}
			} catch (final Exception e) {
				// whatever
			}
		}
		@Override
		public IRegion getHoverRegion(final ITextViewer viewer, final int offset) {
			super.getHoverRegion(viewer, offset);
			final IRegion region = new Region(offset, 0);
			try {
				entityLocator = new EntityLocator(structure(), viewer.getDocument(), region);
			} catch (final Exception e) {
				e.printStackTrace();
				return null;
			}
			return region;
		}
	}

	class DoubleClickStrategy extends DefaultTextDoubleClickStrategy {
		@Override
		protected IRegion findExtendedDoubleClickSelection(final IDocument document, final int pos) {
			final IRegion word = findWord(document, pos);
			try {
				if (word != null && document.get(word.getOffset(), word.getLength()).matches("(\\w|\\|d)+")) {
					return word;
				}
			} catch (final BadLocationException e) {
				return word;
			}
			final Function func = structure().funcAt(pos);
			if (func != null) {
				final ExpressionLocator<Void> locator = new ExpressionLocator<Void>(pos-func.bodyLocation().start());
				func.traverse(locator, null);
				ASTNode expr = locator.expressionAtRegion();
				if (expr == null) {
					return new Region(func.wholeBody().getOffset(), func.wholeBody().getLength());
				} else {
					for (; expr != null; expr = expr.parent()) {
						if (expr instanceof Literal) {
							return new Region(func.bodyLocation().getOffset()+expr.start(), expr.getLength());
						} else if (expr instanceof AccessDeclaration) {
							final AccessDeclaration accessDec = (AccessDeclaration) expr;
							return new Region(func.bodyLocation().getOffset()+accessDec.identifierStart(), accessDec.identifierLength());
						} else if (expr instanceof PropListExpression || expr instanceof Block) {
							return new Region(expr.start()+func.bodyLocation().getOffset(), expr.getLength());
						}
					}
				}
			}
			return null;
		}
	}

	@Override
	protected ContentAssistant createAssistant() { return new Assistant(); }

	@Override
	public Assistant assistant() { return (Assistant) assistant; }

	private final Timer timer = new Timer("ReparseTimer"); //$NON-NLS-1$
	private TimerTask reparseTask, reportFunctionProblemsTask;
	private final Object
		structureModificationLock = new Object(),
		obtainStructureLock = new Object();
	private final ScriptAutoEditStrategy autoEditStrategy = new ScriptAutoEditStrategy(this);


	public ScriptAutoEditStrategy autoEditStrategy() { return autoEditStrategy; }


	@Override
	public void documentAboutToBeChanged(final DocumentEvent event) {}


	@Override
	public void documentChanged(final DocumentEvent event) {
		super.documentChanged(event);
		final Function f = structure().funcAt(event.getOffset());
		if (f != null && !f.isOldStyle()) {
			// editing inside new-style function: adjust locations of declarations without complete reparse
			// only recheck the function and display problems after delay
			scheduleProblemReport(f);
		} else {
			// only schedule reparsing when editing outside of existing function
			scheduleReparsing(false);
		}
	}

	@Override
	protected void adjustDeclaration(final Declaration declaration, final int offset, final int add) {
		super.adjustDeclaration(declaration, offset, add);
		if (declaration instanceof Function) {
			incrementLocationOffsetsExceedingThreshold(((Function)declaration).bodyLocation(), offset, add);
		}
		for (final Declaration v : declaration.subDeclarations(declaration.index(), DeclMask.ALL)) {
			adjustDeclaration(v, offset, add);
		}
	}

	private void reparse() throws ProblemException {
		cancelReparsingTimer();
		reparseWithDocumentContents(refreshEditorsRunnable());
	}


	@Override
	public void refreshAfterBuild(final Markers markers) {
		super.refreshAfterBuild(markers);
		reportProblemsOnFunctionsCalledByActiveFunction(markers);
		oldFunctionBody = null;
	}

	private void reportProblemsOnFunctionsCalledByActiveFunction(final Markers markers) {
		try {
			final IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
			if (part instanceof C4ScriptEditor && ((C4ScriptEditor)part).state() == this) {
				final Function f = ((C4ScriptEditor)part).functionAtCursor();
				if (f != null) {
					for (final ProblemReportingStrategy strategy : problemReportingStrategies()) {
						reportProblemsOnCalledFunctions(f, markers, strategy);
					}
				}
			}
		} catch (final Exception e) {}
	}

	private Runnable refreshEditorsRunnable() {
		return () -> editors.forEach(editor -> {
			editor.refreshOutline();
			attempt(voidResult(editor::handleCursorPositionChanged));
		});
	}

	void reparseWithDocumentContents(final Runnable uiRefreshRunnable) throws ProblemException {
		structure().requireLoaded();

		final Markers markers = new StructureMarkers(false);
		final String source = document.get();
		synchronized (structureModificationLock) {
			new ScriptParser(source, structure(), null) {{
				setMarkers(markers);
				script().clearDeclarations();
				parseDeclarations();
				script().deriveInformation();
				validate();
			}};
			structure().traverse(Comment.TODO_EXTRACTOR, markers);
			reportProblems(markers);
		}
		markers.deploy();
		if (uiRefreshRunnable != null) {
			Display.getDefault().asyncExec(uiRefreshRunnable);
		}
	}

	private void reportProblems(final Markers markers) {
		for (final ProblemReportingStrategy s : problemReportingStrategies()) {
			s.steer(() -> {
				s.initialize(markers, new NullProgressMonitor(), new Script[] {structure()});
				s.run();
				s.apply();
				s.run2();
			});
		}
	}


	public List<ProblemReportingStrategy> problemReportingStrategies() {
		try {
			return ClonkProjectNature.get(structure().file()).problemReportingStrategies();
		} catch (final NullPointerException npe) {
			return new ArrayList<>();
		}
	}


	public void scheduleReparsing(final boolean onlyDeclarations) {
		reparseTask = cancelTimerTask(reparseTask);
		if (timer == null || structure() == null) {
			return;
		}
		timer.schedule(reparseTask = new TimerTask() {
			@Override
			public void run() {
				if (errorsWhileTypingDisabled()) {
					return;
				}
				try {
					try {
						reparseWithDocumentContents(() -> {
							for (final C4ScriptEditor ed : editors) {
								ed.refreshOutline();
								ed.handleCursorPositionChanged();
							}
						});
					} finally {
						cancel();
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}, 1000);
	}


	public static void removeMarkers(final Function func, final Script script) {
		if (script != null && script.resource() != null) {
			try {
				// delete regular markers that are in the region of interest
				final IMarker[] markers = script.resource().findMarkers(Core.MARKER_C4SCRIPT_ERROR, false, 3);
				final SourceLocation body = func != null ? func.bodyLocation() : null;
				for (final IMarker m : markers) {
					// delete marks inside the body region
					final int markerStart = m.getAttribute(IMarker.CHAR_START, 0);
					final int markerEnd   = m.getAttribute(IMarker.CHAR_END, 0);
					if (body == null || (markerStart >= body.start() && markerEnd < body.end())) {
						m.delete();
						continue;
					}
				}
			} catch (final CoreException e) {
				e.printStackTrace();
			}
		}
	}

	private final class StructureMarkers extends Markers {
		StructureMarkers(final boolean functionBodies){
			applyProjectSettings(structure().index());
			if (functionBodies) {
				captureMarkersInFunctionBodies();
			} else {
				captureExistingMarkers(structure().file());
			}
		}
		private void captureMarkersInFunctionBodies() {
			try {
				final Set<IMarker> _captured = new HashSet<>(Arrays.asList(structure().file().findMarkers(Core.MARKER_C4SCRIPT_ERROR, true, IResource.DEPTH_ONE)));
				for (final Iterator<IMarker> it = _captured.iterator(); it.hasNext();) {
					final IMarker c = it.next();
					if (structure().funcAt(c.getAttribute(IMarker.CHAR_START, -1)) == null) {
						it.remove();
					}
				}
				this.capture(_captured);
			} catch (final CoreException e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("serial")
	static class MarkerConfines extends HashSet<ASTNode> implements IMarkerListener {
		public MarkerConfines(final ASTNode... confines) { this.addAll(Arrays.asList(confines)); }
		@Override
		public Decision markerEncountered(
			final Markers markers, final IASTPositionProvider positionProvider,
			final Problem code, final ASTNode node,
			final int markerStart, final int markerEnd, final int flags,
			final int severity, final Object... args
		) {
			if (node == null) {
				return Decision.DropCharges;
			}
			for (final ASTNode confine : this) {
				if (node.containedIn(confine)) {
					return Decision.PassThrough;
				}
			}
			return Decision.DropCharges;
		}
	}

	private void scheduleProblemReport(final Function fn) {
		if (timer == null || errorsWhileTypingDisabled()) {
			return;
		}
		reportFunctionProblemsTask = cancelTimerTask(reportFunctionProblemsTask);
		timer.schedule(reportFunctionProblemsTask = new TimerTask() {
			@Override
			public void run() {
				try {
					if (structure().source() instanceof IResource && C4GroupItem.groupItemBackingResource((IResource) structure().source()) == null) {
						reportProblems((Function) fn.latestVersion()).deploy();
						Display.getDefault().asyncExec(refreshEditorsRunnable());
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}, 1000);
	}


	public Markers reportProblems(final Function function) {
		// ignore this request when errors while typing disabled
		if (errorsWhileTypingDisabled()) {
			return new Markers();
		}

		final Markers markers = new Markers();
		markers.applyProjectSettings(structure().index());

		structure().deriveInformation();
		for (final ProblemReportingStrategy strategy : problemReportingStrategies()) {
			strategy.steer(() -> {
				// visit the function
				strategy.initialize(markers, new NullProgressMonitor(), Arrays.asList(Pair.pair(structure(), function)));
				strategy.captureMarkers();
				strategy.run();
				strategy.apply();
				strategy.run2();
			});
		}
		return markers;
	}

	private void reportProblemsOnCalledFunctions(
		final Function function,
		final Markers markers,
		final ProblemReportingStrategy strategy
	) {
		//clearAnnotations();

		@SuppressWarnings("serial")
		class DepthCallsCollector
			extends HashMap<Pair<Script, Function>, Set<CallDeclaration>>
			implements IASTVisitor<Pair<Script, Function>>
		{
			final Pair<Script, Function> entry = Pair.pair(structure(), function);
			final IASTVisitor<Script> assignmentFollower = (node, context) -> {
				if (node instanceof AccessVar) {
					final AccessVar av = (AccessVar) node;
					final Variable v = as(av.declaration(), Variable.class);
					if (v != null && v.scope() == Scope.LOCAL) {
						final List<AccessVar> vr = context.varReferences().get(av.name());
						if (vr != null) {
							for (final AccessVar o : vr) {
								if (o.declaration() == v) {
									final Function f = o.parent(Function.class);
									if (f != null) {
										follow(f, context);
									}
								}
							}
						}
					}
				}
				return TraversalContinuation.Continue;
			};
			Function.Typing typing = structure().typings().get(function);
			CallDeclaration localCall;
			final int MAX_DEPTH = 4;
			int depth = 0;
			{ function.traverse(this, entry); }
			@Override
			public TraversalContinuation visitNode(final ASTNode node, final Pair<Script, Function> context) {
				if (typing == null) {
					return TraversalContinuation.Cancel;
				}
				if (node instanceof CallDeclaration) {
					if (depth == 0) {
						localCall = (CallDeclaration) node;
					}
					final CallDeclaration cd = (CallDeclaration) node;
					final Function f = as(cd.declaration(), Function.class);
					if (f != null && f.body() != null && !f.isGlobal()) {
						try {
							final IType targetTy = cd.predecessor() != null ? typing.nodeTypes[cd.predecessor().localIdentifier()] : context.first();
							if (targetTy != null) {
								for (final IType t : targetTy) {
									if (t instanceof Script) {
										final Script s = (Script) t;
										follow(f, s);
									}
								}
							}
						} catch (final Exception e) {
							e.printStackTrace();
						}
					}
					if (depth == 0) {
						localCall = null;
					}
				} else if (node instanceof BinaryOp) {
					final BinaryOp op = (BinaryOp) node;
					op.leftSide().traverse(this.assignmentFollower, context.first());
				}
				return TraversalContinuation.Continue;
			}
			private void follow(final Function f, final Script s) {
				if (localCall == null) {
					return;
				}
				final Function fn = s.findFunction(f.name());
				if (fn == null) {
					return;
				}
				final Pair<Script, Function> pair = Pair.pair(s, fn);
				if (!pair.equals(entry)) {
					Set<CallDeclaration> calls = this.get(pair);
					if (calls == null) {
						calls = new HashSet<>(3);
						calls.add(localCall);
						this.put(pair, calls);
						if (depth < MAX_DEPTH) {
							final Function.Typing old = typing;
							typing = s.typings().get(f);
							depth++;
							f.traverse(this, pair);
							depth--;
							typing = old;
						}
					} else {
						calls.add(localCall);
					}
				}
			}
			public Collection<Pair<Script, Function>> expandedFunctionSet() {
				final Set<Pair<Script, Function>> result = new HashSet<>(keySet());
				final Index ndx = function.index();
				for (final Pair<Script, Function> p : keySet()) {
					final Function base = p.second().baseFunction();
					final Script baseDef = as(base.script(), Definition.class);
					if (baseDef != null) {
						ndx.allScripts(item -> {
							if (item != p.first() && item.doesInclude(ndx, baseDef)) {
								final Function ovrld = item.findLocalFunction(base.name(), true);
								if (ovrld != null) {
									result.add(new Pair<Script, Function>(item, ovrld));
								}
							}
						});
					}
				}
				for (final Iterator<Pair<Script, Function>> it = result.iterator(); it.hasNext();) {
					final Pair<Script, Function> p = it.next();
					if (p.first() == structure()) {
						it.remove();
					}
				}
				return result;
			}
		}
		final DepthCallsCollector collector = new DepthCallsCollector();
		if (!collector.isEmpty()) {
			strategy.steer(() -> {
				strategy.initialize(markers, new NullProgressMonitor(), collector.expandedFunctionSet());
				strategy.captureMarkers();
				strategy.run();
				strategy.apply();
			});
		}
	}

	private boolean errorsWhileTypingDisabled() {
		return !ClonkPreferences.toggle(ClonkPreferences.SHOW_ERRORS_WHILE_TYPING, true);
	}


	@Override
	public void cancelReparsingTimer() {
		reparseTask = runAndCancelTimerTask(reparseTask);
		reportFunctionProblemsTask = runAndCancelTimerTask(reportFunctionProblemsTask);
		super.cancelReparsingTimer();
	}


	@Override
	public void cleanupAfterRemoval() {
		if (timer != null) {
			timer.cancel();
		}
		try {
			if (structure().source() instanceof IFile) {
				final IFile file = (IFile)structure().source();
				// might have been closed due to removal of the file - don't cause exception by trying to reparse that file now
				if (file.exists()) {
					reparseWithDocumentContents(null);
				}
			}
		} catch (final ProblemException e) {
			e.printStackTrace();
		}
		super.cleanupAfterRemoval();
	}

	/**
	 *  Created if there is no suitable script to get from somewhere else
	 *  can be considered a hack to make viewing (svn) revisions of a file work
	 */
	private WeakReference<Script> cachedScript = new WeakReference<Script>(null);


	@Override
	public Script structure() {
		synchronized (obtainStructureLock) {
			Script result = cachedScript.get();
			Cases: if (result == null) {
				if (editors.isEmpty()) {
					result = structure;
					break Cases;
				}

				final IEditorInput input = editors.get(0).getEditorInput();
				if (input instanceof ScriptWithStorageEditorInput) {
					result = ((ScriptWithStorageEditorInput)input).script();
					break Cases;
				}

				final IFile f = Utilities.fileFromEditorInput(input);
				if (f != null) {
					final Script script = Script.get(f, true);
					if (script != null) {
						result = script;
						break Cases;
					}
				}

				result = new ScratchScript(editors.get(0));
				cachedScript = new WeakReference<Script>(result);
				try {
					reparse();
					result.traverse((node, parser) -> {
						final AccessDeclaration ad = as(node, AccessDeclaration.class);
						if (ad != null && ad.declaration() != null) {
							ad.setDeclaration(ad.declaration().latestVersion());
						}
						return TraversalContinuation.Continue;
					}, result);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}

			cachedScript = new WeakReference<Script>(result);
			return this.structure = result;
		}
	}


	@Override
	public void invalidate() {
		cachedScript = new WeakReference<Script>(null);
		structure();
		super.invalidate();
	}

	private FunctionBody oldFunctionBody;


	public FunctionFragmentParser updateFunctionFragment(
		final Function function,
		final IASTVisitor<ProblemReporter> observer,
		final boolean typingContextVisitInAnyCase
	) {
		synchronized (structureModificationLock) {
			if (oldFunctionBody == null) {
				oldFunctionBody = function.body();
			}
			final FunctionFragmentParser fparser = new FunctionFragmentParser(document, structure(), function, null);
			final boolean change = fparser.update();
			if (change || (observer != null && typingContextVisitInAnyCase)) {
				for (final ProblemReportingStrategy s : problemReportingStrategies()) {
					s.steer(() -> {
						s.initialize(null, new NullProgressMonitor(), Arrays.asList(Pair.pair(structure(), function)));
						s.setObserver(observer);
						s.run();
						s.apply();
						s.run2();
					});
				}
			}
			return fparser;
		}
	}


	@Override
	public void partActivated(final IWorkbenchPart part) {
		if (editors.contains(part) && structure() != null) {
			final Function cf = ((C4ScriptEditor)part).functionAtCursor();
			new Job("Refreshing problem markers") {
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					structure().requireLoaded();
					final Markers markers = new StructureMarkers(true);
					synchronized (structureModificationLock) {
						reportProblems(markers);
						if (cf != null) {
							for (final ProblemReportingStrategy strategy : problemReportingStrategies()) {
								reportProblemsOnCalledFunctions(cf, markers, strategy);
							}
						}
					}
					markers.deploy();
					Display.getDefault().asyncExec(refreshEditorsRunnable());
					return Status.OK_STATUS;
				}
			}.schedule();
		}
		super.partBroughtToTop(part);
	}


	public Function functionAt(final int offset) {
		final Script script = structure();
		if (script != null) {
			final Function f = script.funcAt(offset);
			return f;
		}
		return null;
	}


	public static class Call {
		public IFunctionCall callFunc;
		public int parmIndex;
		public int parmsStart, parmsEnd;
		public EntityLocator locator;
		public Call(final Function func, final IFunctionCall callFunc2, final ASTNode parm, final EntityLocator locator) {
			this.callFunc = callFunc2;
			this.parmIndex = parm != null ? callFunc2.indexOfParm(parm) : 0;
			this.parmsStart = func.bodyLocation().start()+callFunc2.parmsStart();
			this.parmsEnd = func.bodyLocation().start()+callFunc2.parmsEnd();
			this.locator = locator;
		}
		public ASTNode callPredecessor() {
			return callFunc instanceof ASTNode ? ((ASTNode)callFunc).predecessor() : null;
		}
	}


	public Call innermostFunctionCallParmAtOffset(final int offset) throws BadLocationException, ProblemException {
		final Function f = functionAt(offset);
		if (f == null) {
			return null;
		}
		updateFunctionFragment(f, null, false);
		final EntityLocator locator = new EntityLocator(structure(), document, new Region(offset, 0));
		ASTNode expr;

		// cursor somewhere between parm expressions... locate CallFunc and search
		final int bodyStart = f.bodyLocation().start();
		for (
			expr = locator.expressionAtRegion();
			expr != null;
			expr = expr.parent()
		) {
			if (expr instanceof IFunctionCall && offset-bodyStart >= ((IFunctionCall)expr).parmsStart()) {
				break;
			}
		}
		if (expr != null) {
			final IFunctionCall callFunc = (IFunctionCall) expr;
			ASTNode prev = null;
			for (final ASTNode parm : callFunc.params()) {
				if (bodyStart+parm.end() > offset) {
					if (prev == null) {
						break;
					}
					final String docText = document.get(bodyStart+prev.end(), parm.start()-prev.end());
					final CStyleScanner scanner = new CStyleScanner(docText);
					scanner.eatWhitespace();
					final boolean comma = scanner.read() == ',' && offset+1 > bodyStart+prev.end() + scanner.tell();
					return new Call(f, callFunc, comma ? parm : prev, locator);
				}
				prev = parm;
			}
			return new Call(f, callFunc, prev, locator);
		}
		return null;
	}


	@Override
	public void completionProposalApplied(final DeclarationProposal proposal) {
		autoEditStrategy().completionProposalApplied(proposal);
		try {
			if (proposal.requiresDocumentReparse()) {
				reparse();
			}
		} catch (final ProblemException e) {
			e.printStackTrace();
		}
		Display.getCurrent().asyncExec(() -> {
			for (final C4ScriptEditor ed : editors) {
				ed.showParameters();
			}
		});
		super.completionProposalApplied(proposal);
	}

	protected Function functionFromEntity(final IIndexEntity entity) {
		Function function = null;
		if (entity instanceof Function) {
			function = (Function)entity;
		} else if (entity instanceof Variable) {
			final IType type = ((Variable)entity).type();
			if (type instanceof FunctionType) {
				function = ((FunctionType)type).prototype();
			}
		}
		return function;
	}


	public IIndexEntity mergeFunctions(final int offset, final ScriptEditingState.Call call) {
		IIndexEntity entity = null;
		call.locator.initializePotentialEntities(structure(), null, (ASTNode)call.callFunc);
		Function commono = null;
		final Set<? extends IIndexEntity> potentials = call.locator.potentialEntities();
		if (potentials != null) {
			if (potentials.size() == 1) {
				entity = potentials.iterator().next();
			} else {
				for (final IIndexEntity e : potentials) {
					if (commono == null) {
						commono = new Function(Messages.C4ScriptCompletionProcessor_MultipleCandidates, FunctionScope.PRIVATE);
					}
					entity = commono;
					final Function function = functionFromEntity(e);
					if (function != null) {
						for (int i = 0; i < function.numParameters(); i++) {
							final Variable fpar = function.parameter(i);
							final Variable cpar = commono.numParameters() > i
								? commono.parameter(i) : commono.addParameter(new Variable(fpar.name(), fpar.type()));
							cpar.forceType(structure().typing().unify(cpar.type(), fpar.type()));
							if (!Arrays.asList(cpar.name().split("/")).contains(fpar.name())) {
								cpar.setName(cpar.name()+"/"+fpar.name()); //$NON-NLS-1$
							}
						}
					}
				}
			}
		}
		return entity;
	}

	private static ScannerPerEngine<ScriptCodeScanner> SCANNERS = new ScannerPerEngine<ScriptCodeScanner>(ScriptCodeScanner.class);
	private final ITextDoubleClickStrategy doubleClickStrategy = new DoubleClickStrategy();

	public ScriptEditingState(final IPreferenceStore store) { super(store); }

	@Override
	public String[] getConfiguredContentTypes(final ISourceViewer sourceViewer) { return CStylePartitionScanner.PARTITIONS; }

	@Override
	public ITextDoubleClickStrategy getDoubleClickStrategy(final ISourceViewer sourceViewer, final String contentType) { return doubleClickStrategy; }

	@Override
	public IQuickAssistAssistant getQuickAssistAssistant(final ISourceViewer sourceViewer) {
		final IQuickAssistAssistant assistant = new QuickAssistAssistant();
		assistant.setQuickAssistProcessor(new ScriptQuickAssistProcessor());
		return assistant;
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(final ISourceViewer sourceViewer) {
		final PresentationReconciler reconciler = new PresentationReconciler();
		final ScriptCommentScanner commentScanner = new ScriptCommentScanner(getColorManager(), "COMMENT"); //$NON-NLS-1$
		final ScriptCodeScanner scanner = SCANNERS.get(structure().engine());

		DefaultDamagerRepairer dr = new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, CStylePartitionScanner.CODEBODY);
		reconciler.setRepairer(dr, CStylePartitionScanner.CODEBODY);

		dr = new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, CStylePartitionScanner.STRING);
		reconciler.setRepairer(dr, CStylePartitionScanner.STRING);

		dr = new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

		dr = new DefaultDamagerRepairer(new ScriptCommentScanner(getColorManager(), "JAVADOCCOMMENT"));
		reconciler.setDamager(dr, CStylePartitionScanner.JAVADOC_COMMENT);
		reconciler.setRepairer(dr, CStylePartitionScanner.JAVADOC_COMMENT);

		dr = new DefaultDamagerRepairer(commentScanner);
		reconciler.setDamager(dr, CStylePartitionScanner.COMMENT);
		reconciler.setRepairer(dr, CStylePartitionScanner.COMMENT);

		dr = new DefaultDamagerRepairer(commentScanner);
		reconciler.setDamager(dr, CStylePartitionScanner.MULTI_LINE_COMMENT);
		reconciler.setRepairer(dr, CStylePartitionScanner.MULTI_LINE_COMMENT);

		return reconciler;
	}

	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(final ISourceViewer sourceViewer) {
		return new IHyperlinkDetector[] {
			new ScriptHyperlinkDetector(),
			urlDetector
		};
	}

	@Override
	public IAutoEditStrategy[] getAutoEditStrategies(final ISourceViewer sourceViewer, final String contentType) {
		return new IAutoEditStrategy[] {autoEditStrategy};
	}

	@Override
	public ITextHover getTextHover(final ISourceViewer sourceViewer, final String contentType) {
	    if (hover == null) {
			hover = new ScriptTextHover();
		}
	    return hover;
	}

	@Override
	public IFile file() { return structure().file(); }

	@Override
	public Declaration container() { return structure(); }

	@Override
	public int fragmentOffset() { return 0; }

	public void saved() {
		cancelReparsingTimer();
		assistant().hide();
	}
}