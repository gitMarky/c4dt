package net.arctics.clonk.ui.editors.c4script;

import static net.arctics.clonk.util.Utilities.as;

import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import net.arctics.clonk.Core;
import net.arctics.clonk.Core.IDocumentAction;
import net.arctics.clonk.parser.ASTNode;
import net.arctics.clonk.parser.DeclMask;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.IASTPositionProvider;
import net.arctics.clonk.parser.IASTVisitor;
import net.arctics.clonk.parser.IMarkerListener;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.Problem;
import net.arctics.clonk.parser.SourceLocation;
import net.arctics.clonk.parser.TraversalContinuation;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.FunctionFragmentParser;
import net.arctics.clonk.parser.c4script.PrimitiveType;
import net.arctics.clonk.parser.c4script.ProblemReportingContext;
import net.arctics.clonk.parser.c4script.ProblemReportingStrategy;
import net.arctics.clonk.parser.c4script.ProblemReportingStrategy.Capabilities;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.ast.AccessDeclaration;
import net.arctics.clonk.parser.c4script.ast.CallDeclaration;
import net.arctics.clonk.preferences.ClonkPreferences;
import net.arctics.clonk.resource.c4group.C4GroupItem;
import net.arctics.clonk.ui.editors.StructureEditingState;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;

/**
 * C4Script-specific specialization of {@link StructureEditingState} that tries to only trigger a full reparse of the script when necessary (i.e. not when editing inside of a function)
 * @author madeen
 *
 */
public final class ScriptEditingState extends StructureEditingState<C4ScriptEditor, Script> {

	private static final List<ScriptEditingState> list = new ArrayList<>();

	private final Timer reparseTimer = new Timer("ReparseTimer"); //$NON-NLS-1$
	private TimerTask reparseTask, functionReparseTask;
	private List<ProblemReportingStrategy> problemReportingStrategies;
	private ProblemReportingStrategy typingStrategy;

	@Override
	protected void initialize() {
		super.initialize();
		try {
			problemReportingStrategies = structure.index().nature().instantiateProblemReportingStrategies(0);
			for (final ProblemReportingStrategy strategy : problemReportingStrategies)
				if ((strategy.capabilities() & Capabilities.TYPING) != 0) {
					typingStrategy = strategy;
					break;
				}
		} catch (final Exception e) {
			problemReportingStrategies = Arrays.asList();
		}
	}

	public List<ProblemReportingStrategy> problemReportingStrategies() { return problemReportingStrategies; }
	public ProblemReportingStrategy typingStrategy() { return typingStrategy; }

	public static ScriptEditingState addTo(IDocument document, Script script, C4ScriptEditor client)  {
		try {
			return addTo(list, ScriptEditingState.class, document, script, client);
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {}

	@Override
	public void documentChanged(DocumentEvent event) {
		super.documentChanged(event);
		final Function f = structure.funcAt(event.getOffset());
		if (f != null && !f.isOldStyle())
			// editing inside new-style function: adjust locations of declarations without complete reparse
			// only recheck the function and display problems after delay
			scheduleReparsingOfFunction(f);
		else
			// only schedule reparsing when editing outside of existing function
			scheduleReparsing(false);
	}

	@Override
	protected void adjustDec(Declaration declaration, int offset, int add) {
		super.adjustDec(declaration, offset, add);
		if (declaration instanceof Function)
			incrementLocationOffsetsExceedingThreshold(((Function)declaration).bodyLocation(), offset, add);
		for (final Declaration v : declaration.subDeclarations(declaration.index(), DeclMask.ALL))
			adjustDec(v, offset, add);
	}
	
	private static C4ScriptParser parserForDocument(Object document, final Script script) {
		C4ScriptParser parser = null;
		if (document instanceof IDocument)
			parser = new C4ScriptParser(((IDocument)document).get(), script, script.scriptFile());
		else if (document instanceof IFile)
			parser = Core.instance().performActionsOnFileDocument((IFile) document, new IDocumentAction<C4ScriptParser>() {
				@Override
				public C4ScriptParser run(IDocument document) {
					return new C4ScriptParser(document.get(), script, script.scriptFile());
				}
			}, false);
		if (parser == null)
			throw new InvalidParameterException("document");
		return parser;
	}
	
	private C4ScriptParser reparse(boolean onlyDeclarations) throws ParsingException {
		cancelReparsingTimer();
		return reparseWithDocumentContents(onlyDeclarations, document, structure(), new Runnable() {
			@Override
			public void run() {
				for (final C4ScriptEditor ed : editors) {
					ed.refreshOutline();
					ed.handleCursorPositionChanged();
				}
			}
		});
	}
	
	C4ScriptParser reparseWithDocumentContents(
		boolean onlyDeclarations, Object document,
		final Script script,
		Runnable uiRefreshRunnable
	) throws ParsingException {
		final Markers markers = new Markers();
		final C4ScriptParser parser = parserForDocument(document, script);
		parser.setMarkers(markers);
		parser.clear(!onlyDeclarations, !onlyDeclarations);
		parser.parseDeclarations();
		parser.script().generateCaches();
		parser.validate();
		if (!onlyDeclarations) {
			if (this.typingStrategy() != null) {
				final ProblemReportingContext localTyping = this.typingStrategy().localTypingContext(parser.script(), parser.fragmentOffset(), null);
				localTyping.setMarkers(markers);
				localTyping.reportProblems();
			}
			markers.deploy();
		}
		// make sure it's executed on the ui thread
		if (uiRefreshRunnable != null)
			Display.getDefault().asyncExec(uiRefreshRunnable);
		return parser;
	}

	public void scheduleReparsing(final boolean onlyDeclarations) {
		reparseTask = cancelTimerTask(reparseTask);
		if (structure == null)
			return;
		reparseTimer.schedule(reparseTask = new TimerTask() {
			@Override
			public void run() {
				if (errorsWhileTypingDisabled())
					return;
				try {
					try {
						reparseWithDocumentContents(onlyDeclarations, document, structure, new Runnable() {
							@Override
							public void run() {
								for (final C4ScriptEditor ed : editors) {
									ed.refreshOutline();
									ed.handleCursorPositionChanged();
								}
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

	public static void removeMarkers(Function func, Script script) {
		if (script != null && script.resource() != null)
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

	@SuppressWarnings("serial")
	static class MarkerConfines extends HashSet<ASTNode> implements IMarkerListener {
		public MarkerConfines(ASTNode... confines) {
			this.addAll(Arrays.asList(confines));
		}
		@Override
		public Decision markerEncountered(
			Markers markers, IASTPositionProvider positionProvider,
			Problem code, ASTNode node,
			int markerStart, int markerEnd, int flags,
			int severity, Object... args
		) {
			if (node == null)
				return Decision.DropCharges;
			for (final ASTNode confine : this)
				if (node.containedIn(confine))
					return Decision.PassThrough;
			return Decision.DropCharges;
		}
	}

	private void scheduleReparsingOfFunction(final Function fn) {
		if (errorsWhileTypingDisabled())
			return;
		functionReparseTask = cancelTimerTask(functionReparseTask);
		reparseTimer.schedule(functionReparseTask = new TimerTask() {
			@Override
			public void run() {
				try {
					if (structure.source() instanceof IResource && C4GroupItem.groupItemBackingResource((IResource) structure.source()) == null) {
						removeMarkers(fn, structure);
						final Function f = (Function) fn.latestVersion();
						final Markers markers = reparseFunction(f, ReparseFunctionMode.FULL);
						for (final Variable localVar : f.locals()) {
							final SourceLocation l = localVar;
							l.setStart(f.bodyLocation().getOffset()+l.getOffset());
							l.setEnd(f.bodyLocation().getOffset()+l.end());
						}
						markers.deploy();
					}
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}, 1000);
	}

	private void addCalls(Set<Function> to, Function from) {
		for (final Collection<CallDeclaration> cdc : from.script().callMap().values())
			for (final CallDeclaration cd : cdc)
				if (cd.containedIn(from)) {
					final Function called = as(cd.declaration(), Function.class);
					if (called != null)
						to.add(called);
				}
	}
	
	public enum ReparseFunctionMode {
		/** Revisit called functions so that their parameter types are
		 *  adjusted according to arguments passed here */
		REVISIT_CALLED_FUNCTIONS;
		
		public static final EnumSet<ScriptEditingState.ReparseFunctionMode> FULL = EnumSet.allOf(ScriptEditingState.ReparseFunctionMode.class);
		public static final EnumSet<ScriptEditingState.ReparseFunctionMode> LIGHT = EnumSet.noneOf(ScriptEditingState.ReparseFunctionMode.class);
	}

	public Markers reparseFunction(final Function function, EnumSet<ScriptEditingState.ReparseFunctionMode> mode) {
		// ignore this request when errors while typing disabled
		if (errorsWhileTypingDisabled())
			return new Markers();

		final Markers markers = new Markers(new MarkerConfines(function));
		markers.applyProjectSettings(structure.index());
		
		// compute set of functions that were called by the old non-reparsed version of the function
		// those will be revisited after performing the main visit
		final Set<Function> oldCalledFunctions = mode.contains(ReparseFunctionMode.REVISIT_CALLED_FUNCTIONS) ? new HashSet<Function>() : null;
		if (oldCalledFunctions != null)
			addCalls(oldCalledFunctions, function);
		
		// actual reparsing
		final C4ScriptParser parser = FunctionFragmentParser.update(document, structure, function, markers);
		
		// see above continued
		if (oldCalledFunctions != null) {
			final Set<Function> newCalledFunctions = new HashSet<Function>(oldCalledFunctions.size());
			addCalls(newCalledFunctions, function);
			oldCalledFunctions.removeAll(newCalledFunctions);
		}
		
		// main visit - this will also branch out to called functions so their parameter types will be adjusted taking into account
		// concrete parameters passed from here
		structure.generateCaches();
		for (final ProblemReportingStrategy strategy : problemReportingStrategies) {
			final ProblemReportingContext mainTyping = strategy.localTypingContext(parser.script(), parser.fragmentOffset(), null);
			if (markers != null)
				mainTyping.setMarkers(markers);
			mainTyping.visitFunction(function);
			if (oldCalledFunctions != null)
				revisit(function, markers, oldCalledFunctions, strategy, mainTyping);
		}
		return markers;
	}

	private void revisit(final Function function, final Markers markers, final Set<Function> functions, final ProblemReportingStrategy strategy, final ProblemReportingContext mainTyping) {
		for (final Function fn : functions)
			if (fn != null && mainTyping.triggersRevisit(function, fn))
				if (markers != null && markers.listener() instanceof ScriptEditingState.MarkerConfines)
					if (((ScriptEditingState.MarkerConfines)markers.listener()).add(fn)) {
						removeMarkers(fn, fn.script());
						for (final Variable p : fn.parameters())
							p.forceType(PrimitiveType.UNKNOWN, false);
						final ProblemReportingContext calledTyping = strategy.localTypingContext(fn.parentOfType(Script.class), 0, mainTyping);
						calledTyping.setMarkers(markers);
						calledTyping.visitFunction(fn);
					}
	}

	private boolean errorsWhileTypingDisabled() {
		return !ClonkPreferences.toggle(ClonkPreferences.SHOW_ERRORS_WHILE_TYPING, true);
	}

	@Override
	public void cancelReparsingTimer() {
		reparseTask = cancelTimerTask(reparseTask);
		functionReparseTask = cancelTimerTask(functionReparseTask);
		super.cancelReparsingTimer();
	}

	@Override
	public void cleanupAfterRemoval() {
		if (reparseTimer != null)
			reparseTimer.cancel();
		try {
			if (structure.source() instanceof IFile) {
				final IFile file = (IFile)structure.source();
				// might have been closed due to removal of the file - don't cause exception by trying to reparse that file now
				if (file.exists())
					reparseWithDocumentContents(false, file, structure, null);
			}
		} catch (final ParsingException e) {
			e.printStackTrace();
		}
		super.cleanupAfterRemoval();
	}

	public static ScriptEditingState state(Script script) {
		return stateFromList(list, script);
	}
	
	/**
	 *  Created if there is no suitable script to get from somewhere else
	 *  can be considered a hack to make viewing (svn) revisions of a file work
	 */
	private WeakReference<Script> cachedScript = new WeakReference<Script>(null);
	
	private WeakReference<ProblemReportingContext> cachedDeclarationObtainmentContext;
	
	@Override
	public Script structure() {
		Script result = cachedScript.get();
		if (result != null) {
			this.structure = result;
			return result;
		}

		if (editors.isEmpty())
			return super.structure();
		
		final IEditorInput input = editors.get(0).getEditorInput();
		if (input instanceof ScriptWithStorageEditorInput)
			result = ((ScriptWithStorageEditorInput)input).script();

		if (result == null) {
			final IFile f = Utilities.fileFromEditorInput(input);
			if (f != null) {
				final Script script = Script.get(f, true);
				if (script != null)
					result = script;
			}
		}

		boolean needsReparsing = false;
		if (result == null && cachedScript.get() == null) {
			result = new ScratchScript(editors.get(0));
			needsReparsing = true;
		}
		cachedScript = new WeakReference<Script>(result);
		if (needsReparsing)
			try {
				reparse(false);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		if (result != null)
			result.traverse(new IASTVisitor<Script>() {
				@Override
				public TraversalContinuation visitNode(ASTNode node, Script parser) {
					final AccessDeclaration ad = as(node, AccessDeclaration.class);
					if (ad != null && ad.declaration() != null)
						ad.setDeclaration(ad.declaration().latestVersion());
					return TraversalContinuation.Continue;
				}
			}, result);
		this.structure = result;
		return result;
	}
	
	@Override
	public void invalidate() {
		cachedScript = new WeakReference<Script>(null);
		cachedDeclarationObtainmentContext = new WeakReference<ProblemReportingContext>(null);
		super.invalidate();
	}
	
	public ProblemReportingContext declarationObtainmentContext() {
		if (cachedDeclarationObtainmentContext != null) {
			final ProblemReportingContext ctx = cachedDeclarationObtainmentContext.get();
			if (ctx != null && ctx.script() == structure())
				return ctx;
		}
		ProblemReportingContext r = null;
		for (final ProblemReportingStrategy strategy : problemReportingStrategies())
			if ((strategy.capabilities() & Capabilities.TYPING) != 0) {
				cachedDeclarationObtainmentContext = new WeakReference<ProblemReportingContext>(
					r = strategy.localTypingContext(structure(), 0, null)
				);
				break;
			}
		return r;
	}
	
}