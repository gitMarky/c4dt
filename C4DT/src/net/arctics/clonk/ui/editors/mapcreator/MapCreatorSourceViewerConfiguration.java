package net.arctics.clonk.ui.editors.mapcreator;

import net.arctics.clonk.parser.mapcreator.MapOverlay;
import net.arctics.clonk.parser.mapcreator.MapOverlayBase;
import net.arctics.clonk.ui.editors.ColorManager;
import net.arctics.clonk.ui.editors.ClonkHyperlink;
import net.arctics.clonk.ui.editors.ClonkPartitionScanner;
import net.arctics.clonk.ui.editors.ClonkSourceViewerConfiguration;
import net.arctics.clonk.ui.editors.ScriptCommentScanner;
import net.arctics.clonk.util.Utilities;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;

public class MapCreatorSourceViewerConfiguration extends ClonkSourceViewerConfiguration<MapCreatorEditor> {

	public class MapCreatorHyperlinkDetector implements IHyperlinkDetector {
		@Override
		public IHyperlink[] detectHyperlinks(ITextViewer textViewer,
				IRegion region, boolean canShowMultipleHyperlinks) {
			MapOverlayBase overlay = editor().mapCreator().overlayAt(region.getOffset());
			// link to template (linking other things does not seem to make much sense)
			if (overlay instanceof MapOverlay && ((MapOverlay)overlay).template() != null && region.getOffset()-overlay.location().start() < ((MapOverlay) overlay).template().name().length())
				return new IHyperlink[] {new ClonkHyperlink(new Region(overlay.location().getOffset(), ((MapOverlay) overlay).template().name().length()), ((MapOverlay) overlay).template())};
			return null;
		}
	}

	private MapCreatorCodeScanner scanner = new MapCreatorCodeScanner(ColorManager.instance());

	public MapCreatorSourceViewerConfiguration(IPreferenceStore store, ColorManager colorManager, MapCreatorEditor textEditor) {
		super(store, colorManager, textEditor);
	}
	
	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		
		ScriptCommentScanner commentScanner = new ScriptCommentScanner(getColorManager(), "COMMENT");
		
		DefaultDamagerRepairer dr =
			new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, ClonkPartitionScanner.CODEBODY);
		reconciler.setRepairer(dr, ClonkPartitionScanner.CODEBODY);
		
		dr = new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, ClonkPartitionScanner.STRING);
		reconciler.setRepairer(dr, ClonkPartitionScanner.STRING);
		
		dr = new DefaultDamagerRepairer(scanner);
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
		
		dr = new DefaultDamagerRepairer(commentScanner);
		reconciler.setDamager(dr, ClonkPartitionScanner.COMMENT);
		reconciler.setRepairer(dr, ClonkPartitionScanner.COMMENT);
		
		return reconciler;
	}
	
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		try {
			return new IHyperlinkDetector[] {
				new MapCreatorHyperlinkDetector()
			};
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
//		assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
//		assistant.setContentAssistProcessor(new CodeBodyCompletionProcessor(getEditor(),assistant), ClonkPartitionScanner.C4S_CODEBODY);
		MapCreatorCompletionProcessor processor = new MapCreatorCompletionProcessor(editor());
		assistant.setContentAssistProcessor(processor, IDocument.DEFAULT_CONTENT_TYPE);
		assistant.install(sourceViewer);
		
		assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
		

		//assistant.setRepeatedInvocationMode(true);
		// key sequence is set in constructor of ClonkCompletionProcessor
		
		assistant.setStatusLineVisible(true);
		assistant.setStatusMessage(String.format(Messages.MapCreatorSourceViewerConfiguration_Proposals, Utilities.fileBeingEditedBy(editor()).getName()));
		
		assistant.enablePrefixCompletion(false);
		assistant.enableAutoInsert(true);
		assistant.enableAutoActivation(true);
		
		assistant.enableColoredLabels(true);
		
		return assistant;
	}

}
