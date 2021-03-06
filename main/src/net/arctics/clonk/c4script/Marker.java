package net.arctics.clonk.c4script;

import static net.arctics.clonk.util.Utilities.as;
import static net.arctics.clonk.util.Utilities.eq;

import java.util.Arrays;

import org.eclipse.core.resources.IFile;

import net.arctics.clonk.Problem;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.IASTPositionProvider;
import net.arctics.clonk.util.Hasher;

public class Marker {
	public final Problem code;
	public final int start, end;
	public int severity;
	public final Object[] arguments;
	public final Declaration contextDeclaration;
	public final ASTNode reporter;
	public final IFile scriptFile;
	public final Declaration container;
	public Marker prev, next;
	
	public Marker(final IASTPositionProvider positionProvider, final Problem code, final ASTNode node, final int start, final int end, final int severity, final Object[] args) {
		super();
		this.code = code;
		this.start = start;
		this.end = end;
		this.severity = severity;
		this.arguments = args;

		this.contextDeclaration = node != null ? node.parent(Declaration.class) : null;
		this.reporter = node;
		this.scriptFile = positionProvider.file();
		this.container = positionProvider.container();
	}
	
	@Override
	public String toString() {
		return String.format("%s @(%s)", code.toString(), reporter.toString()); //$NON-NLS-1$
	}
	
	@Override
	public int hashCode() {
		return new Hasher()
			.in(code)
			.in(start)
			.in(end)
			.in(severity)
			.in(arguments.length)
			.in(arguments)
			.in(scriptFile)
			.finish();
	}
	
	@Override
	public boolean equals(Object obj) {
		final Marker other = as(obj, Marker.class);
		return
			other != null &&
			this.code == other.code &&
			this.start == other.start &&
			this.end == other.end &&
			this.severity == other.severity &&
			Arrays.equals(this.arguments, other.arguments) &&
			eq(this.scriptFile, other.scriptFile);
	}
	
}