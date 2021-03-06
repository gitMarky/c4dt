package net.arctics.clonk.ui.editors;

import static java.util.Arrays.stream;
import static net.arctics.clonk.util.Utilities.defaulting;
import static net.arctics.clonk.util.Utilities.eq;
import static net.arctics.clonk.util.Utilities.synchronizing;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import net.arctics.clonk.ast.DeclMask;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.parser.Markers;
import net.arctics.clonk.ui.editors.actions.OpenDeclarationAction;

/**
 * Editing state on a specific {@link Structure}. Shared among all editors editing the file the {@link Structure} was read from.
 * @author madeen
 *
 * @param <EditorType> The type of {@link StructureTextEditor} the state is shared among.
 * @param <StructureType> Type of {@link Structure}.
 */
@SuppressWarnings("restriction")
public abstract class StructureEditingState<EditorType extends StructureTextEditor, StructureType extends Structure>
	extends TextSourceViewerConfiguration
	implements IDocumentListener, IPartListener {

	public class ClonkTextHover implements ITextHover, ITextHoverExtension {
		private IHyperlink hyperlink;
		public ClonkTextHover() { super(); }
		@Override
		public String getHoverInfo(final ITextViewer viewer, final IRegion region) {
			if (hyperlink instanceof EntityHyperlink) {
				final EntityHyperlink clonkHyperlink = (EntityHyperlink) hyperlink;
				final IIndexEntity entity = clonkHyperlink.target();
				if (entity != null) {
					return entity.infoText(structure());
				}
			}
			return null;
		}
		@Override
		public IRegion getHoverRegion(final ITextViewer viewer, final int offset) {
			hyperlink = hyperlinkAtOffset(offset);
			if (hyperlink != null) {
				return hyperlink.getHyperlinkRegion();
			}
			return null;
		}
		@Override
		public IInformationControlCreator getHoverControlCreator() {
			return new IInformationControlCreator() {
				@Override
				public IInformationControl createInformationControl(final Shell parent) {
					return new DefaultInformationControl(parent, new HTMLTextPresenter(true));
				}
			};
		}
	}

	protected final List<EditorType> editors = new LinkedList<EditorType>();
	protected final List<ISourceViewer> viewers = new LinkedList<ISourceViewer>();
	protected StructureType structure;
	protected IDocument document;
	protected List<? extends StructureEditingState<EditorType, StructureType>> list;
	protected ContentAssistant assistant;
	protected ISourceViewer assistantSite;

	protected ContentAssistant installAssistant(final ISourceViewer sourceViewer) {
		if (assistantSite != sourceViewer) {
			if (assistantSite != null) {
				assistant.uninstall();
			}
			assistantSite = sourceViewer;
			assistant.install(sourceViewer);
		}
		return assistant;
	}

	protected ContentAssistant createAssistant() { return new ContentAssistant(); }
	public ContentAssistant assistant() { return assistant; }

	/**
	 * Called after the text change listener was added to a {@link IDocument} -> {@link StructureEditingState} map.
	 */
	protected void initialize() {
		assistant = createAssistant();
		document.addDocumentListener(this);
	}

	static final Map<Class<? extends StructureEditingState<?, ?>>, List<? extends StructureEditingState<?, ?>>> lists = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static <S extends Structure, T extends StructureEditingState<?, S>> T existing(final Class<T> cls, final S structure) {
		final List<T> list = synchronizing(lists, () -> (List<T>)lists.get(cls));
		return list != null ? list.stream().filter(s -> s.structure() == structure).findFirst().orElse(null) : null;
	}

	/**
	 * Add a text change listener of some supplied listener class to a {@link IDocument} -> {@link StructureEditingState} map.
	 * If there is already a listener in the map matching the document, this listener will be returned instead.
	 * @param type The listener class
	 * @param document The document
	 * @param structure The {@link Structure} corresponding to the document
	 * @param editor One editor editing the document.
	 * @param list The listeners map
	 * @param <E> The type of {@link StructureTextEditor} the listener needs to apply for.
	 * @param <S> The type of {@link Structure} the listener needs to apply for.
	 * @param <T> The type of {@link StructureEditingState} to add.
	 * @return The listener that has been either created and added to the map or was already found in the map.
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings("unchecked")
	public static <E extends StructureTextEditor, S extends Structure, T extends StructureEditingState<E, S>> T request(
		final Class<T> type,
		final IDocument document,
		final S structure,
		final E editor
	) {
		final List<T> list = synchronizing(lists, () -> defaulting(
			(List<T>) lists.get(type),
			() -> {
				final List<T> newList = new LinkedList<T>();
				lists.put(type, newList);
				return newList;
			}
		));
		final StructureEditingState<? super E, ? super S> result = stateFromList(list, structure);
		T r;
		if (result == null) {
			try {
				r = type.getConstructor(IPreferenceStore.class).newInstance(editor.preferenceStore());
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
				InvocationTargetException | NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
				return null;
			}
			r.set(list, structure, document);
			r.initialize();
			list.add(r);
		} else {
			r = (T)result;
		}
		r.addEditor(editor);
		return r;
	}

	public void set(final List<? extends StructureEditingState<EditorType, StructureType>> list, final StructureType structure, final IDocument document) {
		this.list = list;
		this.structure = structure;
		this.document = document;
	}

	protected static <E extends StructureTextEditor, S extends Structure, T extends StructureEditingState<E, S>> T stateFromList(final List<T> list, final S structure) {
		return list.stream().filter(s -> eq(s.structure(), structure)).findFirst().orElse(null); 
	}

	/*+
	 * Cancel a pending timed reparsing of the document.
	 */
	public void cancelReparsingTimer() {}

	/**
	 * Perform some cleanup after all corresponding editors have been closed.
	 */
	public void cleanupAfterRemoval() {}

	protected void addEditor(final EditorType editor) {
		editors.add(editor);
		editor.getSite().getPage().addPartListener(this);
	}

	/**
	 * Remove an editor
	 * @param editor The editor
	 */
	public void removeEditor(final EditorType editor) {
		synchronized (editors) {
			if (editors.remove(editor)) {
				maybeRemovePartListener(editor);
				if (editors.isEmpty()) {
					cancelReparsingTimer();
					list.remove(this);
					document.removeDocumentListener(this);
					cleanupAfterRemoval();
				}
			}
		}
	}

	private void maybeRemovePartListener(final EditorType removedEditor) {
		final boolean removePartListener = editors.stream()
			.noneMatch(editor -> editor.getSite().getPage() == removedEditor.getSite().getPage());
		if (removePartListener) {
			removedEditor.getSite().getPage().removePartListener(this);
		}
	}

	@Override
	public void documentAboutToBeChanged(final DocumentEvent event) {}

	@Override
	public void documentChanged(final DocumentEvent event) { adjustDeclarationLocations(event); }

	/**
	 * Increment the components of some {@link SourceLocation} in-place that exceed a certain threshold.
	 * @param location The location to potentially modify
	 * @param threshold The threshold after which start and end offsets in the location will be incremented.
	 * @param add The value to add to the applicable offsets.
	 */
	protected void incrementLocationOffsetsExceedingThreshold(final SourceLocation location, final int threshold, final int add) {
		if (location != null) {
			if (location.start() > threshold) {
				location.setStart(location.start()+add);
			}
			if (location.end() >= threshold) {
				location.setEnd(location.end()+add);
			}
		}
	}

	/**
	 * Adjust locations stored in a {@link Declaration} according to a call to {@link #incrementLocationOffsetsExceedingThreshold(SourceLocation, int, int)} for each of those locations.
	 * @param declaration The {@link Declaration}
	 * @param threshold The threshold to pass to {@link #incrementLocationOffsetsExceedingThreshold(SourceLocation, int, int)}
	 * @param add The increment value to pass to {@link #incrementLocationOffsetsExceedingThreshold(SourceLocation, int, int)}
	 */
	protected void adjustDeclaration(final Declaration declaration, final int threshold, final int add) {
		incrementLocationOffsetsExceedingThreshold(declaration, threshold, add);
	}

	/**
	 * Call {@link #adjustDeclaration(Declaration, int, int)} for all applicable {@link Declaration}s stored in {@link #structure()}
	 * @param event Document event describing the document change that triggered this call.
	 */
	protected void adjustDeclarationLocations(final DocumentEvent event) {
		if (event.getLength() == 0 && event.getText().length() > 0) {
			// text was added
			for (final Declaration dec : structure.subDeclarations(structure.index(), DeclMask.ALL)) {
				adjustDeclaration(dec, event.getOffset(), event.getText().length());
			}
		} else if (event.getLength() > 0 && event.getText().length() == 0) {
			// text was removed
			for (final Declaration dec : structure.subDeclarations(structure.index(), DeclMask.ALL)) {
				adjustDeclaration(dec, event.getOffset(), -event.getLength());
			}
		} else {
			final String newText = event.getText();
			final int replLength = event.getLength();
			final int offset = event.getOffset();
			final int diff = newText.length() - replLength;
			// mixed
			for (final Declaration dec : structure.subDeclarations(structure.index(), net.arctics.clonk.ast.DeclMask.ALL)) {
				if (dec.start() >= offset + replLength) {
					adjustDeclaration(dec, offset, diff);
				} else if (dec instanceof Function) {
					// inside function: expand end location
					final Function func = (Function) dec;
					if (offset >= func.bodyLocation().start() && offset+replLength < func.bodyLocation().end()) {
						func.bodyLocation().setEnd(func.bodyLocation().end()+diff);
					}
				}
			}
		}
	}

	/**
	 * Cancel a {@link TimerTask} having been fired by this listener. May be null in which case nothing happens
	 * @param whichTask The task to cancel
	 * @return null so this method can be called and its return value be used as assignment right side with the TimerTask reference variable being on the left.
	 */
	public TimerTask cancelTimerTask(final TimerTask whichTask) {
		if (whichTask != null) {
			try {
				whichTask.cancel();
			} catch (final IllegalStateException e) {}
		}
		return null;
	}

	public TimerTask runAndCancelTimerTask(final TimerTask whichTask) {
		if (whichTask != null) {
			whichTask.run();
		}
		return cancelTimerTask(whichTask);
	}

	/**
	 * The structure this listener corresponds to.
	 * @return The {@link Structure}
	 */
	public StructureType structure() { return structure; }

	/**
	 * Invalidate the {@link #structure()} reference and recompute.
	 */
	public void invalidate() {
		document.removeDocumentListener(this);
		boolean failed;
		do {
			failed = false;
			try {
				document = editors.get(0).getDocumentProvider().getDocument(editors.get(0).getEditorInput());
			} catch (final NullPointerException np) {
				editors.remove(0);
				failed = true;
			}
		} while (failed);
		document.addDocumentListener(this);
	}

	@Override
	public void partActivated(final IWorkbenchPart part) {}
	
	@Override
	public void partBroughtToTop(final IWorkbenchPart part) {}
	
	@SuppressWarnings("unchecked")
	@Override
	public void partClosed(final IWorkbenchPart part) {
		try { removeEditor((EditorType) part); }
		catch (final ClassCastException cce) {}
	}
	
	@Override
	public void partDeactivated(final IWorkbenchPart part) {}
	
	@Override
	public void partOpened(final IWorkbenchPart part) {}

	public void completionProposalApplied(final DeclarationProposal proposal) {}

	@SuppressWarnings("unchecked")
	public EditorType activeEditor() {
		final IEditorPart activeEditor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		return editors.contains(activeEditor) ? (EditorType)activeEditor : null;
	}

	protected ITextHover hover;
	
	public StructureEditingState(final IPreferenceStore store) { super(store); }
	
	public ColorManager getColorManager() { return ColorManager.INSTANCE; }
	
	@Override
	public String[] getConfiguredContentTypes(final ISourceViewer sourceViewer) {
		return CStylePartitionScanner.PARTITIONS;
	}
	
	protected static final URLHyperlinkDetector urlDetector = new URLHyperlinkDetector();
	
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(final ISourceViewer sourceViewer) {
		return new IHyperlinkDetector[] { urlDetector };
	}
	
	@Override
	public ITextHover getTextHover(final ISourceViewer sourceViewer, final String contentType) {
		return defaulting(hover, () -> hover = new ClonkTextHover());
	}
	
	@Override
	public IReconciler getReconciler(final ISourceViewer sourceViewer) { return null; }
	
	/**
	 * Create a {@link IHyperlink} at the given offset in the text document using the same mechanism that is being used to create hyperlinks when ctrl-hovering.
	 * This hyperlink will be used for functionality like {@link OpenDeclarationAction} that will not directly operate on specific kinds of {@link Declaration}s and is thus dependent on the {@link StructureTextEditor} class returning adequate hyperlinks.
	 * @param offset The offset
	 * @return
	 */
	public IHyperlink hyperlinkAtOffset(final int offset) {
		final ISourceViewer sourceViewer = editors.get(0).getProtectedSourceViewer();
		final IRegion r = new Region(offset, 0);
		// emulate
		getHyperlinkPresenter(sourceViewer).hideHyperlinks();
		return stream(getHyperlinkDetectors(sourceViewer))
			.map(detector -> detector.detectHyperlinks(sourceViewer, r, false))
			.filter(hyperlinks -> hyperlinks != null && hyperlinks.length > 0)
			.map(hyperlinks -> hyperlinks[0])
			.findFirst()
			.orElse(null);
	}
	
	@Override
	public ContentAssistant getContentAssistant(final ISourceViewer sourceViewer) { return installAssistant(sourceViewer); }

	public void refreshAfterBuild(final Markers markers) {}
}