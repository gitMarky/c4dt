package net.arctics.clonk.ui.editors.c4script;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.SimpleScriptStorage;
import net.arctics.clonk.parser.SourceLocation;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.C4ScriptParser.IMarkerListener;
import net.arctics.clonk.parser.c4script.C4Variable;
import net.arctics.clonk.parser.c4script.IStoredTypeInformation;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.AccessVar;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.CallFunc;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.resource.c4group.C4GroupItem;
import net.arctics.clonk.ui.editors.ClonkCompletionProposal;
import net.arctics.clonk.ui.editors.ClonkPartitionScanner;
import net.arctics.clonk.ui.editors.ExternalScriptsDocumentProvider;
import net.arctics.clonk.ui.editors.IClonkCommandIds;
import net.arctics.clonk.ui.editors.ClonkTextEditor;
import net.arctics.clonk.ui.editors.ColorManager;
import net.arctics.clonk.ui.editors.IHasEditorRefWhichEnablesStreamlinedOpeningOfDeclarations;
import net.arctics.clonk.ui.editors.actions.c4script.TidyUpCodeAction;
import net.arctics.clonk.ui.editors.actions.c4script.FindReferencesAction;
import net.arctics.clonk.ui.editors.actions.c4script.RenameDeclarationAction;
import net.arctics.clonk.util.Utilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;

public class C4ScriptEditor extends ClonkTextEditor {

	private static final class ScratchScript extends C4ScriptBase implements IHasEditorRefWhichEnablesStreamlinedOpeningOfDeclarations {
		private transient final C4ScriptEditor me;
		private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
		
		private static ClonkIndex scratchIndex = new ClonkIndex() {
			private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
		};

		private ScratchScript(C4ScriptEditor me) {
			this.me = me;
		}

		@Override
		public ClonkIndex getIndex() {
			return scratchIndex;
		}

		@Override
		public Object getScriptFile() {
			IDocument document = me.getDocumentProvider().getDocument(me.getEditorInput());
			try {
				return new SimpleScriptStorage(me.getEditorInput().toString(), document.get());
			} catch (UnsupportedEncodingException e) {
				return null;
			}
		}

		@Override
		public ITextEditor getEditor() {
			return me;
		}
	}

	// Helper class that takes care of triggering a timed reparsing when the document is changed and such
	// it tries to only fire a reparse when necessary (ie not when editing inside of a function)
	private final static class TextChangeListener implements IDocumentListener {
		
		private static final int REPARSE_DELAY = 700;
		
		private Timer reparseTimer = new Timer("ReparseTimer"); //$NON-NLS-1$
		private TimerTask reparseTask, functionReparseTask;
		private List<C4ScriptEditor> clients = new LinkedList<C4ScriptEditor>();
		private C4ScriptBase script;
		private IDocument document;
		
		private static Map<IDocument, TextChangeListener> listeners = new HashMap<IDocument, TextChangeListener>();

		public static TextChangeListener addTo(IDocument document, C4ScriptBase script, C4ScriptEditor client) {
			TextChangeListener result = listeners.get(document);
			if (result == null) {
				result = new TextChangeListener();
				result.script = script;
				result.document = document;
				document.addDocumentListener(result);
				listeners.put(document, result);
			}
			result.clients.add(client);
			return result;
		}
		
		public void documentAboutToBeChanged(DocumentEvent event) {
		}

		private void addToLocation(SourceLocation location, int offset, int add) {
			if (location != null) {
				if (location.getStart() > offset)
					location.setStart(location.getStart()+add);
				if (location.getEnd() >= offset)
					location.setEnd(location.getEnd()+add);
			}
		}

		private void adjustDec(C4Declaration declaration, int offset, int add) {
			addToLocation(declaration.getLocation(), offset, add);
			if (declaration instanceof C4Function) {
				C4Function f = (C4Function) declaration;
				addToLocation(f.getBody(), offset, add);
				for (C4Declaration v : f.allSubDeclarations()) {
					addToLocation(v.getLocation(), offset, add);
				}
			}
		}

		public void documentChanged(DocumentEvent event) {
			script.setDirty(true);			
			adjustDeclarationLocations(event);
			final C4Function f = script.funcAt(event.getOffset());
			if (f != null && !f.isOldStyle()) {
				// editing inside new-style function: adjust locations of declarations without complete reparse
				// only recheck the function and display problems after delay
				scheduleReparsingOfFunction(f);
			} else {
				// only schedule reparsing when editing outside of existing function
				scheduleReparsing();
			}
		}

		private void adjustDeclarationLocations(DocumentEvent event) {
			if (event.getLength() == 0 && event.getText().length() > 0) {
				// text was added
				for (C4Declaration dec : script.allSubDeclarations()) {
					adjustDec(dec, event.getOffset(), event.getText().length());
				}
			}
			else if (event.getLength() > 0 && event.getText().length() == 0) {
				// text was removed
				for (C4Declaration dec : script.allSubDeclarations()) {
					adjustDec(dec, event.getOffset(), -event.getLength());
				}
			}
			else {
				String newText = event.getText();
				int replLength = event.getLength();
				int offset = event.getOffset();
				int diff = newText.length() - replLength;
				// mixed
				for (C4Declaration dec : script.allSubDeclarations()) {
					if (dec.getLocation().getStart() >= offset + replLength)
						adjustDec(dec, offset, diff);
					else if (dec instanceof C4Function) {
						// inside function: expand end location
						C4Function func = (C4Function) dec;
						if (offset >= func.getBody().getStart() && offset+replLength < func.getBody().getEnd()) {
							func.getBody().setEnd(func.getBody().getEnd()+diff);
						}
					}
				}
			}
		}
		
		public TimerTask cancel(TimerTask whichTask) {
			if (whichTask != null) {
				try {
					whichTask.cancel();
				} catch (IllegalStateException e) {
					System.out.println("happens all the time, bitches");
				}
			}
			return null;
		}

		private void scheduleReparsing() {
			reparseTask = cancel(reparseTask);
			if (script == null)
				return;
			reparseTimer.schedule(reparseTask = new TimerTask() {
				@Override
				public void run() {
					try {
						try {
							reparseWithDocumentContents(null, true, document, script, new Runnable() {
								@Override
								public void run() {
									for (C4ScriptEditor ed : clients) {
										ed.refreshOutline();
										ed.handleCursorPositionChanged();
									}
								}
							});
						} finally {
							cancel();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, REPARSE_DELAY);
		}
		
		public static void removeMarkers(C4Function func, C4ScriptBase script) {
			if (script != null && script.getResource() != null) {
				try {
					// delete all "while typing" errors
					IMarker[] markers = script.getResource().findMarkers(ClonkCore.MARKER_C4SCRIPT_ERROR_WHILE_TYPING, false, 3);
					for (IMarker m : markers) {
						m.delete();
					}
					// delete regular markers that are in the region of interest
					markers = script.getResource().findMarkers(ClonkCore.MARKER_C4SCRIPT_ERROR, false, 3);
					SourceLocation body = func != null ? func.getBody() : null;
					for (IMarker m : markers) {
						int markerStart = m.getAttribute(IMarker.CHAR_START, 0);
						int markerEnd   = m.getAttribute(IMarker.CHAR_END, 0);
						if (body == null || (markerStart >= body.getStart() && markerEnd < body.getEnd())) {
							m.delete();
						}
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
		
		private void scheduleReparsingOfFunction(final C4Function f) {
			functionReparseTask = cancel(functionReparseTask);
			reparseTimer.schedule(functionReparseTask = new TimerTask() {
				public void run() {
					removeMarkers(f, script);
					if (script.getScriptFile() instanceof IResource && !C4GroupItem.isLinkedResource((IResource) script.getScriptFile())) {
						C4ScriptParser.reportExpressionsAndStatements(document, f.getBody(), script, f, null, new IMarkerListener() {
							@Override
							public void markerEncountered(ParserErrorCode code,
									int markerStart, int markerEnd, boolean noThrow,
									int severity, Object... args) {
								if (script.getScriptFile() instanceof IFile)
									code.createMarker((IFile) script.getScriptFile(), script, ClonkCore.MARKER_C4SCRIPT_ERROR_WHILE_TYPING, markerStart, markerEnd, severity, args);
							}
						});
					}
				}
			}, REPARSE_DELAY);
		}

		public void removeClient(C4ScriptEditor client) {
			clients.remove(client);
			if (clients.size() == 0) {
				cancel();
				listeners.remove(document);
				document.removeDocumentListener(this);
				try {
					if (script.getScriptFile() instanceof IFile) {
						IFile file = (IFile)script.getScriptFile();
						reparseWithDocumentContents(null, false, file, script, null);
					}
				} catch (ParsingException e) {
					e.printStackTrace();
				}
			}
		}

		public void cancel() {
			reparseTask = cancel(reparseTask);
			functionReparseTask = cancel(functionReparseTask);
		}
	}

	private ColorManager colorManager;
	private static final String ENABLE_BRACKET_HIGHLIGHT = ClonkCore.id("enableBracketHighlighting"); //$NON-NLS-1$
	private static final String BRACKET_HIGHLIGHT_COLOR = ClonkCore.id("bracketHighlightColor"); //$NON-NLS-1$
	
	private DefaultCharacterPairMatcher fBracketMatcher = new DefaultCharacterPairMatcher(new char[] { '{', '}', '(', ')', '[', ']' });
	private TextChangeListener textChangeListener;
	
	public C4ScriptEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new C4ScriptSourceViewerConfiguration(getPreferenceStore(), colorManager,this));
		//setDocumentProvider(new ClonkDocumentProvider(this));
	}

	@Override
	protected void setDocumentProvider(IEditorInput input) {
		if (input instanceof ScriptWithStorageEditorInput) {
			setDocumentProvider(new ExternalScriptsDocumentProvider(this));
		} else {
			super.setDocumentProvider(input);
		}
	}
	
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		
		// set partitioner (FIXME: remove again?)
		IDocument document = getDocumentProvider().getDocument(input);
		if (document.getDocumentPartitioner() == null) {
			IDocumentPartitioner partitioner =
				new FastPartitioner(
					new ClonkPartitionScanner(),
					ClonkPartitionScanner.C4S_PARTITIONS
				);
			partitioner.connect(document);
			document.setDocumentPartitioner(partitioner);
		}
	}
	
	@Override
	protected void editorSaved() {
		if (textChangeListener != null)
			textChangeListener.cancel();
		if (scriptBeingEdited() instanceof ScratchScript) {
			try {
				reparseWithDocumentContents(null, false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		super.editorSaved();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#configureSourceViewerDecorationSupport(org.eclipse.ui.texteditor.SourceViewerDecorationSupport)
	 */
	@Override
	protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
		super.configureSourceViewerDecorationSupport(support);
		support.setCharacterPairMatcher(fBracketMatcher);
		support.setMatchingCharacterPainterPreferenceKeys(ENABLE_BRACKET_HIGHLIGHT, BRACKET_HIGHLIGHT_COLOR);
		getPreferenceStore().setValue(ENABLE_BRACKET_HIGHLIGHT, true);
		PreferenceConverter.setValue(getPreferenceStore(), BRACKET_HIGHLIGHT_COLOR, new RGB(0x33,0x33,0xAA));
	}
	
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		C4ScriptBase script = scriptBeingEdited();
		if (script != null && script.isEditable())
			textChangeListener = TextChangeListener.addTo(getDocumentProvider().getDocument(getEditorInput()), script, this);
	}

	public void dispose() {
		if (textChangeListener != null) {
			textChangeListener.removeClient(this);
		}
		colorManager.dispose();
		super.dispose();
	}
	
	private static final ResourceBundle messagesBundle = ResourceBundle.getBundle(ClonkCore.id("ui.editors.c4script.actionsBundle")); //$NON-NLS-1$
	
	protected void createActions() {
		super.createActions();

		IAction action;
		
		action = new TidyUpCodeAction(messagesBundle,"TidyUpCode.",this); //$NON-NLS-1$
		setAction(IClonkCommandIds.CONVERT_OLD_CODE_TO_NEW_CODE, action);
		
		action = new FindReferencesAction(messagesBundle,"FindReferences.",this); //$NON-NLS-1$
		setAction(IClonkCommandIds.FIND_REFERENCES, action);
		
		action = new RenameDeclarationAction(messagesBundle, "RenameDeclaration.", this); //$NON-NLS-1$
		setAction(IClonkCommandIds.RENAME_DECLARATION, action);
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#editorContextMenuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
	protected void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);
		if (scriptBeingEdited() != null) {
			if (scriptBeingEdited().isEditable()) {
				addAction(menu, IClonkCommandIds.CONVERT_OLD_CODE_TO_NEW_CODE);
				addAction(menu, IClonkCommandIds.RENAME_DECLARATION);
			}
			addAction(menu, IClonkCommandIds.FIND_REFERENCES);
		}
	}
	
	private int cursorPos() {
		return ((TextSelection)getSelectionProvider().getSelection()).getOffset();
	}

	@Override
	protected void handleCursorPositionChanged() {
		super.handleCursorPositionChanged();
		
		C4Function f = getFuncAtCursor();
		// show parameter help
		ITextOperationTarget opTarget = (ITextOperationTarget) getSourceViewer();
		try {
			if (PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart() == this)
				if (!getContentAssistant().isProposalPopupActive())
					if (opTarget.canDoOperation(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION))
						opTarget.doOperation(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);
		} catch (NullPointerException nullP) {
			// might just be not that much of an issue
		}
		
		// highlight active function
		boolean noHighlight = true;
		if (f != null) {
			this.setHighlightRange(f.getLocation().getOffset(), Math.min(
				f.getBody().getOffset()-f.getLocation().getOffset() + f.getBody().getLength() + (f.isOldStyle()?0:1),
				this.getDocumentProvider().getDocument(getEditorInput()).getLength()-f.getLocation().getOffset()
			), false);
			noHighlight = false;
		}
		if (noHighlight)
			this.resetHighlightRange();
		
		// inform auto edit strategy about cursor position change so it can delete its override regions
		getC4ScriptSourceViewerConfiguration().getAutoEditStrategy().handleCursorPositionChanged(
			cursorPos(), getDocumentProvider().getDocument(getEditorInput()));

	}

	private final C4ScriptSourceViewerConfiguration getC4ScriptSourceViewerConfiguration() {
		return (C4ScriptSourceViewerConfiguration)getSourceViewerConfiguration();
	}

	@Override
	public void completionProposalApplied(ClonkCompletionProposal proposal) {
		getC4ScriptSourceViewerConfiguration().getAutoEditStrategy().completionProposalApplied(proposal);
		super.completionProposalApplied(proposal);
	}

	// created if there is no suitable script to get from somewhere else
	// can be considered a hack to make viewing (svn) revisions of a file work
	private C4ScriptBase scratchScript;
	
	public C4ScriptBase scriptBeingEdited() {
		if (getEditorInput() instanceof ScriptWithStorageEditorInput) {
			return ((ScriptWithStorageEditorInput)getEditorInput()).getScript();
		}
		IFile f;
		if ((f = Utilities.getEditingFile(this)) != null) {
			C4ScriptBase script = C4ScriptBase.get(f, true);
			if (script != null)
				return script;
		}

		if (scratchScript == null) {
			scratchScript = new ScratchScript(this);
			try {
				reparseWithDocumentContents(null, false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return scratchScript;
	}

	public C4Function getFuncAt(int offset) {
		C4ScriptBase script = scriptBeingEdited();
		if (script != null) {
			C4Function f = script.funcAt(offset);
			return f;
		}
		return null;
	}
	
	public C4Function getFuncAtCursor() {
		return getFuncAt(cursorPos());
	}

	public C4ScriptParser reparseWithDocumentContents(C4ScriptExprTree.IExpressionListener exprListener, boolean onlyDeclarations) throws IOException, ParsingException {
		if (scriptBeingEdited() == null)
			return null;
		IDocument document = getDocumentProvider().getDocument(getEditorInput());
		return reparseWithDocumentContents(exprListener, onlyDeclarations, document, scriptBeingEdited(), new Runnable() {
			public void run() {
				refreshOutline();
				handleCursorPositionChanged();
			}
		});
	}

	private static C4ScriptParser reparseWithDocumentContents(
			C4ScriptExprTree.IExpressionListener exprListener,
			boolean onlyDeclarations, Object document,
			C4ScriptBase script,
			Runnable uiRefreshRunnable)
			throws ParsingException {
		C4ScriptParser parser;
		if (document instanceof IDocument) {
			parser = new C4ScriptParser(((IDocument)document).get(), script, null);
		} else if (document instanceof IFile) {
			parser = new C4ScriptParser(Utilities.stringFromFile((IFile)document), script, (IFile)document);
		} else {
			throw new InvalidParameterException("document");
		}
		List<IStoredTypeInformation> storedLocalsTypeInformation = null;
		if (onlyDeclarations) {
			// when only parsing declarations store type information for variables declared in the script
			// and apply that information back to the variables after having reparsed so that type information is kept like it was (resulting from a full parse)
			storedLocalsTypeInformation = new LinkedList<IStoredTypeInformation>();
			for (C4Variable v : script.variables()) {
				IStoredTypeInformation info = v.getType() != null || v.getObjectType() != null ? AccessVar.createStoredTypeInformation(v) : null;
				if (info != null)
					storedLocalsTypeInformation.add(info);
			}
		}
		parser.setExpressionListener(exprListener);
		parser.clean();
		parser.parseDeclarations();
		if (!onlyDeclarations)
			parser.parseCodeOfFunctionsAndValidate();
		if (storedLocalsTypeInformation != null) {
			for (IStoredTypeInformation info : storedLocalsTypeInformation) {
				info.apply(false);
			}
		}
		// make sure it's executed on the ui thread
		if (uiRefreshRunnable != null)
			Display.getDefault().asyncExec(uiRefreshRunnable);
		return parser;
	}
	
	@Override
	public C4ScriptBase getTopLevelDeclaration() {
		return scriptBeingEdited();
	}
	
	public static class FuncCallInfo {
		public CallFunc callFunc;
		public int parmIndex;
		public int parmsStart, parmsEnd;
		public FuncCallInfo(C4Function func, CallFunc callFunc, int parmIndex) {
			this.callFunc = callFunc;
			this.parmIndex = parmIndex;
			this.parmsStart = func.getBody().getStart()+callFunc.getParmsStart();
			this.parmsEnd = func.getBody().getStart()+callFunc.getParmsEnd();
		}
		public FuncCallInfo(C4Function func, CallFunc callFunc, ExprElm parm) {
			this(func, callFunc, parm != null ? callFunc.indexOfParm(parm) : 0);
		}
	}

	public FuncCallInfo getInnermostCallFuncExprParm(int offset) throws BadLocationException, ParsingException {
		C4Function f = this.getFuncAt(offset);
		if (f == null)
			return null;
		DeclarationLocator locator = new DeclarationLocator(this, getSourceViewer().getDocument(), new Region(offset, 0));
		ExprElm expr;

		// cursor somewhere between parm expressions... locate CallFunc and search
		int bodyStart = f.getBody().getStart();
		for (
			expr = locator.getExprAtRegion();
			expr != null;
			expr = expr.getParent()
		) {
			 if (expr instanceof CallFunc && offset-f.getBody().getOffset() >= ((CallFunc)expr).getParmsStart())
				 break;
		}
		if (expr != null) {
			CallFunc callFunc = (CallFunc) expr;
			ExprElm prev = null;
			for (ExprElm parm : callFunc.getParams()) {
				if (bodyStart+parm.getExprEnd() > offset) {
					if (prev == null)
						break;
					String docText = getSourceViewer().getDocument().get(bodyStart+prev.getExprEnd(), parm.getExprStart()-prev.getExprEnd());
					int commaIndex = docText.indexOf(',');
					return new FuncCallInfo(f, callFunc, offset >= bodyStart+prev.getExprEnd()+commaIndex ? parm : prev);
				}
				prev = parm;
			}
			return new FuncCallInfo(f, callFunc, prev);
		}
		return null;
	}

}
