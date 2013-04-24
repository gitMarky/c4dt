package net.arctics.clonk.command;

import net.arctics.clonk.Core;
import net.arctics.clonk.ProblemException;
import net.arctics.clonk.c4script.C4ScriptParser;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.typing.dabble.DabbleInference;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.util.SelfcontainedStorage;

import org.eclipse.core.resources.IStorage;

public class SelfContainedScript extends Script {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	protected String script;

	public SelfContainedScript(String name, String script, Index index) {
		super(index);
		setName(name);
		this.script = script;
		final C4ScriptParser parser = new C4ScriptParser(script, this, null);
		try {
			parser.parse();
			generateCaches();
			new DabbleInference().localTypingContext(parser.script(), parser.fragmentOffset(), null).reportProblems();
		} catch (final ProblemException e) {
			e.printStackTrace();
		}
	}

	@Override
	public IStorage source() {
		return new SelfcontainedStorage(name(), script);
	}

}