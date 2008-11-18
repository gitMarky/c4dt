package net.arctics.clonk.ui.editors;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.Utilities;
import net.arctics.clonk.parser.C4Field;
import net.arctics.clonk.parser.C4Function;
import net.arctics.clonk.parser.C4ID;
import net.arctics.clonk.parser.C4Object;
import net.arctics.clonk.parser.C4ObjectExtern;
import net.arctics.clonk.parser.C4ObjectIntern;
import net.arctics.clonk.parser.C4ScriptParser;
import net.arctics.clonk.parser.CompilerException;
import net.arctics.clonk.parser.C4ScriptExprTree.ExprAccessField;
import net.arctics.clonk.parser.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.parser.C4ScriptExprTree.ExprID;
import net.arctics.clonk.parser.C4ScriptExprTree.FieldRegion;
import net.arctics.clonk.parser.C4ScriptExprTree.IExpressionListener;
import net.arctics.clonk.parser.C4ScriptExprTree.TraversalContinuation;
import net.arctics.clonk.parser.C4ScriptParser.ParsingException;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

@SuppressWarnings("restriction")
public class ClonkSourceViewerConfiguration extends TextSourceViewerConfiguration {
	
	/**
	 * Encapsulates information about an identifier in a document and the field it refers to
	 * @author madeen
	 *
	 */
	private class IdentInfo implements IExpressionListener {
		private String line;
		private IRegion identRegion;
		private ExprElm exprAtRegion;
		private C4Field field;
		
		public IdentInfo(IDocument doc, IRegion region) throws BadLocationException, CompilerException, ParsingException {
			C4Object obj = Utilities.getObjectForEditor(getEditor());
			C4Function func = obj.funcAt(region);
			if (func == null) {
				// outside function, fallback to old technique (only ids)
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
				identRegion = new Region(lineInfo.getOffset()+start,end-start);
				if (identRegion.getLength() == 4) {
					C4ID id = C4ID.getID(doc.get(identRegion.getOffset(), identRegion.getLength()));
					field = Utilities.getObjectForEditor(getEditor()).getIndex().getLastObjectWithId(id);
					if (field == null)
						field = ClonkCore.EXTERN_INDEX.getLastObjectWithId(id);
				}
				return;
			}
			int statementStart = func.getBody().getOffset();
			identRegion = new Region(region.getOffset()-statementStart,0);
			C4ScriptParser parser = C4ScriptParser.reportExpressionsInStatements(doc, func.getBody(), obj, func, this);
			if (exprAtRegion != null) {
				FieldRegion fieldRegion = exprAtRegion.fieldAt(identRegion.getOffset()-exprAtRegion.getExprStart(), parser);
				if (fieldRegion != null) {
					this.field = fieldRegion.getField();
					this.identRegion = new Region(statementStart+fieldRegion.getRegion().getOffset(), fieldRegion.getRegion().getLength());
				}
				
			}
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
			return identRegion;
		}

		/**
		 * @return the field
		 */
		public C4Field getField() {
			return field;
		}

		public TraversalContinuation expressionDetected(ExprElm expression) {
			expression.traverse(new IExpressionListener() {
				public TraversalContinuation expressionDetected(ExprElm expression) {
					if (identRegion.getOffset() >= expression.getExprStart() && identRegion.getOffset() < expression.getExprEnd()) {
						exprAtRegion = expression;
						return TraversalContinuation.TraverseSubElements;
					}
					return TraversalContinuation.Continue;
				}
			});
			return exprAtRegion != null ? TraversalContinuation.Cancel : TraversalContinuation.Continue;
		}
	}
	
	private class C4ScriptHyperlinkDetector implements IHyperlinkDetector {

		public IHyperlink[] detectHyperlinks(ITextViewer viewer, IRegion region, boolean canShowMultipleHyperlinks) {
			IdentInfo i;
			try {
				i = new IdentInfo(viewer.getDocument(),region);
			} catch (Exception e) {
				e.printStackTrace();
				i = null;
			}
			if (i != null && i.getField() != null && (i.getField().getObject() != null || i.getField() instanceof C4Object)) {
				return new IHyperlink[] {
					new C4ScriptHyperlink(i.getIdentRegion(),i.getField())
				};
			} else {
				return null;
			}
		}
		
	}
	
	private static class C4ScriptHyperlink implements IHyperlink {

		private final IRegion region;
		private C4Field target;
		
		/**
		 * @param region
		 * @param target
		 */
		public C4ScriptHyperlink(IRegion region, C4Field target) {
			super();
			this.region = region;
			this.target = target;
		}

		public IRegion getHyperlinkRegion() {
			return region;
		}

		public String getHyperlinkText() {
			return target.getName();
		}

		public String getTypeLabel() {
			return "C4Script Hyperlink";
		}

		public void open() {
			IWorkbench workbench = PlatformUI.getWorkbench();
			IWorkbenchPage workbenchPage = workbench.getActiveWorkbenchWindow().getActivePage();
			try {
				C4Object obj = target instanceof C4Object ? (C4Object)target : target.getObject();
				if (obj!= null) {
					if (obj instanceof C4ObjectIntern) {
						IEditorPart editor = workbenchPage.openEditor(new FileEditorInput((IFile) obj.getScript()), "clonk.editors.C4ScriptEditor");
						C4ScriptEditor scriptEditor = (C4ScriptEditor)editor;						
						if (target != obj) {
							scriptEditor.reparseWithDocumentContents(null, false);
							target = target.latestVersion();
							if (target != null)
								scriptEditor.selectAndReveal(target.getLocation());
						}
					}
					else if (obj instanceof C4ObjectExtern) {
						if (obj != ClonkCore.ENGINE_OBJECT) {
							IEditorPart editor = workbenchPage.openEditor(new ObjectExternEditorInput((C4ObjectExtern)obj), "clonk.editors.C4ScriptEditor");
							C4ScriptEditor scriptEditor = (C4ScriptEditor)editor;
							if (target != obj)
								scriptEditor.selectAndReveal(target.getLocation());
						}
					}
				} else {
					// TODO: provide some info about global functions or something
				}
			} catch (PartInitException e) {
				e.printStackTrace();
			} catch (CompilerException e) {
				//e.printStackTrace();
			}
		}
		
	}
	
	private class C4ScriptTextHover implements ITextHover, ITextHoverExtension {

		private IdentInfo identInfo;
//		private IInformationControlCreator informationControlCreator;
		
		public C4ScriptTextHover() {
			super();
			//informationControlCreator = new C4ScriptTextHoverCreator();
		}
		
		public String getHoverInfo(ITextViewer viewer, IRegion region) {
			return identInfo != null && identInfo.getField() != null
				? identInfo.getField().getShortInfo()
				: null;
		}

		public IRegion getHoverRegion(ITextViewer viewer, int offset) {
			try {
				identInfo = new IdentInfo(viewer.getDocument(), new Region(offset, 0));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return identInfo.getIdentRegion();
		}

		public IInformationControlCreator getHoverControlCreator() {
			return new IInformationControlCreator() {
				public IInformationControl createInformationControl(Shell parent) {
					return new DefaultInformationControl(parent, new HTMLTextPresenter(true));
				}
			};
		}
		
	}
	
	private ClonkDoubleClickStrategy doubleClickStrategy;
	private ClonkCodeScanner scanner;
	private ClonkCommentScanner commentScanner;
	private ColorManager colorManager;
	private ITextEditor textEditor;

	public ClonkSourceViewerConfiguration(ColorManager colorManager, ITextEditor textEditor) {
		this.colorManager = colorManager;
		this.textEditor = textEditor;
	}
	
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return ClonkPartitionScanner.C4S_PARTITIONS;
	}
	
	public ITextDoubleClickStrategy getDoubleClickStrategy(
		ISourceViewer sourceViewer,
		String contentType) {
		if (doubleClickStrategy == null)
			doubleClickStrategy = new ClonkDoubleClickStrategy();
		return doubleClickStrategy;
	}

	protected ITextEditor getEditor() {
		return textEditor;
	}
	
	protected ClonkCodeScanner getClonkScanner() {
		if (scanner == null) {
			scanner = new ClonkCodeScanner(colorManager);
			scanner.setDefaultReturnToken(
				new Token(
					new TextAttribute(
						colorManager.getColor(IClonkColorConstants.DEFAULT))));
		}
		return scanner;
	}
	
	protected ClonkCommentScanner getClonkCommentScanner() {
		if (commentScanner == null) {
			commentScanner = new ClonkCommentScanner(colorManager);
			commentScanner.setDefaultReturnToken(
				new Token(
					new TextAttribute(
						colorManager.getColor(IClonkColorConstants.COMMENT))));
		}
		return commentScanner;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getTabWidth(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public int getTabWidth(ISourceViewer sourceViewer) {		
		return 2;
	}

	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
//		assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
//		assistant.setContentAssistProcessor(new CodeBodyCompletionProcessor(getEditor(),assistant), ClonkPartitionScanner.C4S_CODEBODY);
		assistant.setContentAssistProcessor(new ClonkCompletionProcessor(getEditor(),assistant), IDocument.DEFAULT_CONTENT_TYPE);
		assistant.install(sourceViewer);
		
		assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
		
		assistant.setRepeatedInvocationMode(true);
		
		assistant.setStatusLineVisible(true);
		assistant.setStatusMessage("Standard proposals");
		
		assistant.enablePrefixCompletion(false);
		assistant.enableAutoInsert(true);
		assistant.enableAutoActivation(true);
		
		assistant.enableColoredLabels(true);
		
		assistant.setInformationControlCreator(new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
//				BrowserInformationControl control = new BrowserInformationControl(parent, "Arial", "Press 'Tab' from proposal table or click for focus");
				DefaultInformationControl def = new DefaultInformationControl(parent,"Press 'Tab' from proposal table or click for focus");
				return def;
			}
		});
		
		
		return assistant;
	}
	
	@Override
	public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
		IQuickAssistAssistant assistant = new QuickAssistAssistant();
		assistant.setQuickAssistProcessor(new ClonkQuickAssistProcessor());
		return assistant;
	}
	
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		
		DefaultDamagerRepairer dr =
			new DefaultDamagerRepairer(getClonkScanner());
		reconciler.setDamager(dr, ClonkPartitionScanner.C4S_CODEBODY);
		reconciler.setRepairer(dr, ClonkPartitionScanner.C4S_CODEBODY);
		
		dr = new DefaultDamagerRepairer(getClonkScanner());
		reconciler.setDamager(dr, ClonkPartitionScanner.C4S_STRING);
		reconciler.setRepairer(dr, ClonkPartitionScanner.C4S_STRING);
		
		dr = new DefaultDamagerRepairer(getClonkScanner());
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
		
		dr = new DefaultDamagerRepairer(getClonkCommentScanner());
		reconciler.setDamager(dr, ClonkPartitionScanner.C4S_COMMENT);
		reconciler.setRepairer(dr, ClonkPartitionScanner.C4S_COMMENT);
		
		dr = new DefaultDamagerRepairer(getClonkCommentScanner());
		reconciler.setDamager(dr, ClonkPartitionScanner.C4S_MULTI_LINE_COMMENT);
		reconciler.setRepairer(dr, ClonkPartitionScanner.C4S_MULTI_LINE_COMMENT);
		
//		NonRuleBasedDamagerRepairer ndr =
//			new NonRuleBasedDamagerRepairer(
//				new TextAttribute(
//					colorManager.getColor(IClonkColorConstants.COMMENT)));
//		
//		reconciler.setDamager(ndr, ClonkPartitionScanner.C4S_COMMENT);
//		reconciler.setRepairer(ndr, ClonkPartitionScanner.C4S_COMMENT);
		
		return reconciler;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getHyperlinkDetectors(org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) { 
		return new IHyperlinkDetector[] {
				new C4ScriptHyperlinkDetector(),
				new URLHyperlinkDetector()
		};
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getTextHover(org.eclipse.jface.text.source.ISourceViewer, java.lang.String, int)
	 */
	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType, int stateMask) {
		return new C4ScriptTextHover();
	}

}