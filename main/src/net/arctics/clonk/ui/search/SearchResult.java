package net.arctics.clonk.ui.search;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IRegion;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;

import net.arctics.clonk.FileDocumentActions;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.parser.BufferedScanner;

/**
 * Represents a search result for the various searches.
 * @author madeen
 *
 */
public class SearchResult extends AbstractTextSearchResult {
	
	private final SearchQuery query;
	private final Map<Structure, BufferedScanner> scanners = new HashMap<>();
	
	/**
	 * Create as the result of the specified query.
	 * @param query The query the created object is to be the result of
	 */
	public SearchResult(final SearchQuery query) { this.query = query; }
	
	@Override
	public IEditorMatchAdapter getEditorMatchAdapter() { return query; }
	@Override
	public IFileMatchAdapter getFileMatchAdapter() { return query; }
	@Override
	public ImageDescriptor getImageDescriptor() { return null; }
	@Override
	public String getLabel() { return query.getLabel(); }
	@Override
	public ISearchQuery getQuery() { return query; }
	@Override
	public String getTooltip() { return null; }
	
	/**
	 * Add a match described by an {@link ASTNode}.
	 * @param structure The {@link Structure} in which the match was found
	 * @param match Node. Relevant properties describing the location of the match are {@link ASTNode#identifierStart()}, {@link ASTNode#identifierLength()} plus {@link ASTNode#sectionOffset()}.
	 * @param potential Flag indicating that the match is only potential and not definite
	 * @param indirect Flag indicating whether the match indirectly refers to the declaration references were searched for.
	 */
	public void addIdentifierMatch(final Structure structure, final ASTNode node, final boolean potential, final boolean indirect) {
		BufferedScanner scanner;
		synchronized (scanners) {
			scanner = scanners.get(structure);
			if (scanner == null) {
				scanner = FileDocumentActions.performActionOnFileDocument(structure.file(),
					document -> new BufferedScanner(document.get()),
					false
				);
				scanners.put(structure, scanner);
			}
		}
		final IRegion lineRegion = scanner.regionOfLineContainingRegion(node.absolute());
		final String line = scanner.bufferSubstringAtRegion(lineRegion);
		addMatch(new SearchMatch(line, lineRegion.getOffset(), structure, node, potential, indirect, false));
	}
	
	/**
	 * Clear internally maintained list of {@link BufferedScanner}s that were created for each file in which a match was found.
	 */
	public void clearScanners() { scanners.clear(); }
}