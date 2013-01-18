package net.arctics.clonk.ui.search;

import static net.arctics.clonk.util.Utilities.threadPool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import net.arctics.clonk.Core;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.parser.ExprElm;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.ast.IASTVisitor;
import net.arctics.clonk.parser.c4script.ast.TraversalContinuation;
import net.arctics.clonk.util.Sink;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

public class C4ScriptSearchQuery extends SearchQueryBase {
	
	public static class Match extends ClonkSearchMatch {
		private final ExprElm matched;
		private final Map<String, Object> subst;
		public ExprElm matched() { return matched; }
		public Map<String, Object> subst() { return subst; }
		public Match(String line, int lineOffset, Object element, int offset, int length, ExprElm matched, Map<String, Object> subst) {
			super(line, lineOffset, element, offset, length, false, false);
			this.matched = matched;
			this.subst = subst;
		}
	}
	
	private void addMatch(ExprElm match, C4ScriptParser parser, int s, int l, Map<String, Object> subst) {
		Match m = match(match, parser, s, l, subst);
		result.addMatch(m);
	}

	protected static Match match(ExprElm match, C4ScriptParser parser, int s, int l, Map<String, Object> subst) {
		IRegion lineRegion = parser.regionOfLineContainingRegion(new Region(s, l));
		String line = parser.bufferSubstringAtRegion(lineRegion);
		Match m = new Match(line, lineRegion.getOffset(), parser.script(), s, l, match, subst);
		return m;
	}

	private final String templateText;
	private final ExprElm template;
	private final ExprElm replacement;
	private final Iterable<Script> scope;

	public ExprElm replacement() { return replacement; }
	public ExprElm template() { return template; }
	
	private Engine commonEngine(Iterable<Script> scripts) {
		Engine e = null;
		for (Script s : scripts)
			if (e == null)
				e = s.engine();
			else if (e != s.engine())
				throw new IllegalArgumentException("Scripts from different engines");
		return e;
	}
	
	public C4ScriptSearchQuery(String templateExpressionText, String replacementExpressionText, Iterable<Script> scope) throws ParsingException {
		this.templateText = templateExpressionText;
		Engine engine = commonEngine(scope);
		this.template = C4ScriptParser.matchingExpr(templateExpressionText, engine);
		this.replacement = replacementExpressionText != null ? C4ScriptParser.matchingExpr(replacementExpressionText, engine) : null;
		this.scope = scope;
	}
	
	@Override
	public IStatus run(IProgressMonitor monitor) throws OperationCanceledException {
		threadPool(new Sink<ExecutorService>() {
			@Override
			public void receivedObject(ExecutorService item) {
				class ScriptSearcher implements Runnable, IASTVisitor {
					private final C4ScriptParser parser;
					private final Map<String, Match> matches = new HashMap<String, Match>();
					public ScriptSearcher(Script script) {
						C4ScriptParser p = null;
						try {
							p = new C4ScriptParser(script);
						} catch (Exception e) {
							System.out.println(String.format("Creating parser failed for '%s'", script));
						}
						parser = p;
					}
					@Override
					public void run() {
						if (parser == null)
							return;
						for (Function f : parser.script().functions()) {
							f.code().traverse(this, parser);
							commitMatches();
						}
						for (Variable v : parser.script().variables())
							if (v.initializationExpression() != null) {
								v.initializationExpression().traverse(this, parser);
								commitMatches();
							}
					}
					private void commitMatches() {
						for (Match m : matches.values())
							result.addMatch(m);
						matches.clear();
					}
					@Override
					public TraversalContinuation visitExpression(ExprElm expression, C4ScriptParser parser) {
						Map<String, Object> subst = template.match(expression);
						if (subst != null) {
							IRegion r = expression.absolute();
							addMatch(expression, parser, r.getOffset(), r.getLength(), subst);
							return TraversalContinuation.SkipSubElements;
						} else
							return TraversalContinuation.Continue;
					}
				}
				for (final Script s : scope)
					if (s.scriptFile() != null)
						item.execute(new ScriptSearcher(s));
			}
		}, 20);
		return new Status(IStatus.OK, Core.PLUGIN_ID, 0, "C4Script Search Success", null);
	}

	@Override
	public String getLabel() {
		return String.format("Search for '%s'", templateText); 
	}

}
