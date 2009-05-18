package net.arctics.clonk.ui.editors;

import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.texteditor.ITextEditor;

public class ClonkSourceViewerConfiguration extends
		TextSourceViewerConfiguration {
	private ITextEditor textEditor;
	private ColorManager colorManager;

	public ClonkSourceViewerConfiguration(ColorManager colorManager, ITextEditor textEditor) {
		this.textEditor = textEditor;
		this.colorManager = colorManager;
	}
	
	public ColorManager getColorManager() {
		return colorManager;
	}

	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return ClonkPartitionScanner.C4S_PARTITIONS;
	}

	protected ITextEditor getEditor() {
		return textEditor;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getHyperlinkDetectors(org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) { 
		return new IHyperlinkDetector[] {
				new URLHyperlinkDetector()
		};
	}

}
