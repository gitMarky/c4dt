package net.arctics.clonk.builder;

import static net.arctics.clonk.util.Utilities.runWithoutAutoBuild;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import net.arctics.clonk.FileDocumentActions;
import net.arctics.clonk.c4script.ScriptParser;
import net.arctics.clonk.c4script.typing.TypeAnnotation;

final class DynamicTypingMigrationJob extends TypingMigrationJob {
	private final ScriptParser[] parsers;
	private final ProjectSettings settings;

	DynamicTypingMigrationJob(final ClonkProjectNature nature, final String name, final ScriptParser[] parsers, final ProjectSettings settings) {
		super(name, nature);
		this.parsers = parsers;
		this.settings = settings;
	}

	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		monitor.beginTask("Dynamic Typing Migration", parsers.length);
		runWithoutAutoBuild(() -> {
			for (final ScriptParser parser : parsers) {
				if (parser != null && parser.script() != null && parser.script().file() != null) {
					removeTypeAnnotations(parser);
				}
				monitor.worked(1);
			}
		});
		settings.concludeTypingMigration();
		nature.saveSettings();
		return Status.OK_STATUS;
	}

	private void removeTypeAnnotations(final ScriptParser parser) {
		if (parser.typeAnnotations() == null) {
			return;
		}
		FileDocumentActions.performActionOnFileDocument(parser.script().file(), document -> {
			final StringBuilder builder = new StringBuilder(document.get());
			final List<TypeAnnotation> annotations = parser.typeAnnotations();
			Collections.sort(annotations);
			for (int i = annotations.size()-1; i >= 0; i--) {
				final TypeAnnotation annot = annotations.get(i);
				int end = annot.end();
				if (end < builder.length() && Character.isWhitespace(builder.charAt(end))) {
					end++;
				}
				builder.delete(annot.start(), end);
			}
			document.set(builder.toString());
			return null;
		}, true);
	}
}