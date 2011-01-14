package net.arctics.clonk.command;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IStorage;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.SimpleScriptStorage;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.ast.Block;
import net.arctics.clonk.parser.c4script.ast.ExprElm;
import net.arctics.clonk.parser.c4script.ast.ScriptParserListener;
import net.arctics.clonk.parser.c4script.ast.Statement;
import net.arctics.clonk.parser.c4script.ast.TraversalContinuation;

public class ExecutableScript extends ScriptBase {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	private String script;
	private BodyPreservingFunction main;
	private ClonkIndex index;

	@Override
	public ClonkIndex getIndex() {
		return index;
	}

	@Override
	public String getScriptText() {
		return script;
	}

	@Override
	public IStorage getScriptStorage() {
		try {
			return new SimpleScriptStorage(getName(), script);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public ExecutableScript(String name, String script, ClonkIndex index) {
		super();
		setName(name);
		this.script = script;
		this.index = index;
		C4ScriptParser parser = new C4ScriptParser(script, this, null) {
			@Override
			protected Function newFunction(String nameWillBe) {
				return new BodyPreservingFunction();
			}
			@Override
			public void parseCodeOfFunction(Function function) throws ParsingException {
				if (function.getName().equals("Main")) { //$NON-NLS-1$
					main = (BodyPreservingFunction)function;
				}
				final List<Statement> statements = new LinkedList<Statement>();
				this.setListener(new ScriptParserListener() {
					@Override
					public TraversalContinuation expressionDetected(ExprElm expression, C4ScriptParser parser) {
						if (expression instanceof Statement)
							statements.add((Statement)expression);
						return TraversalContinuation.Continue;
					}
				});
				super.parseCodeOfFunction(function);
				((BodyPreservingFunction)function).setBodyBlock(new Block(statements));
				this.setListener(null);
			}
		};
		try {
			parser.parse();
		} catch (ParsingException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Collection<ScriptBase> getIncludes(ClonkIndex index) {
		return Arrays.asList(Command.COMMAND_BASESCRIPT);
	}

	public BodyPreservingFunction getMain() {
		return main;
	}

	public Object invoke(Object... args) {
		return main.invoke(args);
	}

}