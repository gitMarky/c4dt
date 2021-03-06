package net.arctics.clonk.builder;

import static java.util.Arrays.stream;
import static net.arctics.clonk.util.Utilities.eq;
import static net.arctics.clonk.util.Utilities.runWithoutAutoBuild;

import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import net.arctics.clonk.FileDocumentActions;
import net.arctics.clonk.c4script.ScriptParser;
import net.arctics.clonk.c4script.typing.IType;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.c4script.typing.TypeAnnotation;

final class StaticTypingMigrationJob extends TypingMigrationJob {
	
	private final ProjectSettings settings;
	private final ScriptParser[] parsers;
	
	StaticTypingMigrationJob(final String name, final ClonkProjectNature nature, final ProjectSettings settings, final ScriptParser[] parsers) {
		super(name, nature);
		this.settings = settings;
		this.parsers = parsers;
	}
	
	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		monitor.beginTask("Static Typing Migration", parsers.length);
		runWithoutAutoBuild(() -> {
			stream(parsers)
				.filter(parser -> parser != null && parser.script() != null && parser.script().file() != null)
				.forEach(this::insertTypeAnnotations);
			monitor.worked(1);
		});
		settings.concludeTypingMigration();
		nature.saveSettings();
		return Status.OK_STATUS;
	}
	
	private void insertTypeAnnotations(final ScriptParser parser) {
		FileDocumentActions.performActionOnFileDocument(parser.script().file(), document -> {
			final StringBuilder builder = new StringBuilder(document.get());
			final List<TypeAnnotation> annotations = parser.typeAnnotations();
			final Integer changes = annotations.stream()
				.sorted(Comparator.reverseOrder())
				.filter(annotation -> annotation.type() == null && annotation.target() != null)
				.map(annotation -> {
					builder.delete(annotation.start(), annotation.end());
					builder.insert(annotation.start(), " ");
					final IType t1 = annotation.target().type();
					final IType t2 = eq(t1, PrimitiveType.UNKNOWN) ? PrimitiveType.ANY : t1;
					builder.insert(annotation.start(), t2.typeName(false));
					return 1;
				})
				.reduce((c, a) -> c + a)
				.orElse(0);
			if (changes > 0) {
				document.set(builder.toString());
			}
			return null;
		}, true);
	}

}