package net.arctics.clonk.index;

import static java.util.Arrays.stream;
import static net.arctics.clonk.util.Utilities.attemptWithResource;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.eclipse.core.runtime.IProgressMonitor;

import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.c4script.Function;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.c4script.Variable;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.c4script.ast.Ellipsis;
import net.arctics.clonk.c4script.typing.PrimitiveType;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.util.IStorageLocation;
import net.arctics.clonk.util.StreamUtil;

/**
 * Helper to import declarations from source files. Geared towards OpenClonk source.
 * @author madeen
 *
 */
public class CPPSourceDeclarationsImporter {

	public boolean overwriteExistingDeclarations = true;

	/**
	 * Import declarations from a source repository, putting them in the supplied {@link Script} container.
	 * Source file paths are fetched from the container's engine's settings' {@link EngineSettings#cppSources}
	 * @param importsContainer The container to import the declarations into. Usually an {@link Engine}
	 * @param repository Repository path to prepend to cpp source paths
	 * @param monitor Monitor used to monitor the progress of the operation.
	 */
	public void importFromRepository(final Script importsContainer, IStorageLocation location, final IProgressMonitor monitor) {
		final URL scriptDefsLoc = location.locatorForEntry("scriptdefinitionsources.txt", false);
		final String[] sourceFiles = scriptDefsLoc != null
			? attemptWithResource(
				() -> scriptDefsLoc.openStream(), s -> StreamUtil.stringFromInputStream(s).split("\n"),
				IOException.class,
				Exception::printStackTrace
			)
			: importsContainer.engine().settings().cppSources.split(",");
		stream(sourceFiles).forEach(
			sourceFile -> readDeclarationsFromSource(importsContainer, location, sourceFile.trim())
		);
		if (monitor != null) {
			monitor.done();
		}
	}

	private void readDeclarationsFromSource(final Script importsContainer, final IStorageLocation location, final String sourceFilePath) {

		final String origin = sourceFilePath;

		final int SECTION_None = 0;
		final int SECTION_InitFunctionMap = 1;
		final int SECTION_C4ScriptConstMap = 2;
		final int SECTION_C4ScriptFnMap = 3;

		final URL sourceFile = location.locatorForEntry(sourceFilePath, false);
		final EngineSettings settings = importsContainer.engine().settings();
		if (sourceFile != null) {
			final Matcher[] sectionStartMatchers = new Matcher[] {
				Pattern.compile(settings.initFunctionMapPattern).matcher(""),
				Pattern.compile(settings.constMapPattern).matcher(""),
				Pattern.compile(settings.fnMapPattern).matcher("")
			};
			final Matcher fnMapEntryMatcher = Pattern.compile(settings.fnMapEntryPattern).matcher(""); //$NON-NLS-1$
			final Matcher constMapEntryMatcher = Pattern.compile(settings.constMapEntryPattern).matcher(""); //$NON-NLS-1$
			final Matcher addFuncMatcher = Pattern.compile(settings.addFuncPattern).matcher(""); //$NON-NLS-1$
			final Matcher fnDeclarationMatcher = Pattern.compile(settings.fnDeclarationPattern).matcher("");

			try {
				final BufferedScanner scanner = new BufferedScanner(sourceFile);
				int section = SECTION_None;
				int lineOffset = 0;
				Outer: for (String line = scanner.readLine(); !scanner.reachedEOF(); lineOffset = scanner.tell(), line = scanner.readLine()) {
					// determine section
					for (int s = 0; s < sectionStartMatchers.length; s++) {
						sectionStartMatchers[s].reset(line);
						if (sectionStartMatchers[s].matches()) {
							section = s+1;
							continue Outer;
						}
					}

					switch (section) {
					case SECTION_InitFunctionMap:
						if (addFuncMatcher.reset(line).matches()) {
							final String name = addFuncMatcher.group(1);
							Function fun = importsContainer.findLocalFunction(name, false);
							if (fun == null) {
								fun = new DocumentedFunction(name, PrimitiveType.ANY, origin);
								fun.setLocation(new SourceLocation(lineOffset, lineOffset+line.length()));
								fun.setParameters(
									new Variable(Ellipsis.ELLIPSIS, PrimitiveType.ANY)
								);
								importsContainer.addDeclaration(fun);
							}
							continue Outer;
						}
						break;
					case SECTION_C4ScriptConstMap:
						if (constMapEntryMatcher.reset(line).matches()) {
							int i = 1;
							final String name = constMapEntryMatcher.group(i++);
							final String typeString = constMapEntryMatcher.group(i++);
							PrimitiveType type;
							try {
								type = PrimitiveType.fromString(typeString.substring(4).toLowerCase());
							} catch (final Exception e) {
								type = PrimitiveType.INT;
							}

							Variable cnst = importsContainer.findLocalVariable(name, false);
							if (cnst == null) {
								cnst = new DocumentedVariable(name, type);
								cnst.setScope(Scope.CONST);
								importsContainer.addDeclaration(cnst);
							}
							continue Outer;
						}
						break;
					case SECTION_C4ScriptFnMap:
						if (fnMapEntryMatcher.reset(line).matches()) {
							int i = 1;
							final String name = fnMapEntryMatcher.group(i++);
							i++;//String public_ = fnMapMatcher.group(i++);
							final String retType = fnMapEntryMatcher.group(i++);
							final String parms = fnMapEntryMatcher.group(i++);
							//String pointer = fnMapMatcher.group(i++);
							//String oldPointer = fnMapMatcher.group(i++);
							Function fun = importsContainer.findLocalFunction(name, false);
							if (fun == null) {
								final String[] parameterNames = parms.split(","); //$NON-NLS-1$
								fun = new DocumentedFunction(name, PrimitiveType.fromString(retType.substring(4).toLowerCase(), true), origin);
								fun.setLocation(new SourceLocation(lineOffset, lineOffset+line.length()));
								fun.setParameters(
									IntStream.range(0, parameterNames.length)
										.mapToObj(index -> new Variable(
											"par"+(index + 1),
											PrimitiveType.fromString(parameterNames[index].trim().substring(4).toLowerCase(), true)
										))
										.toArray(length -> new Variable[length])
								);
								importsContainer.addDeclaration(fun);
							}
							continue Outer;
						}
						break;
					}

					if (fnDeclarationMatcher.reset(line).matches()) {
						int i = 1;
						final String returnType = fnDeclarationMatcher.group(i++);
						final String name = fnDeclarationMatcher.group(i++);
						// some functions to be ignored
						if (name.equals("_goto") || name.equals("_this")) {
							continue;
						}
						i++; // optional Object in C4AulContext
						i++; // optional actual parameters with preceding comma
						final String parms = fnDeclarationMatcher.group(i++);
						Function fun = importsContainer.findLocalFunction(name, false);
						if (fun == null) {
							fun = new DocumentedFunction(name, PrimitiveType.fromCPPString(returnType), origin);
							fun.setLocation(new SourceLocation(lineOffset, lineOffset+line.length()));
							final String[] parmStrings = parms != null ? parms.split("\\,") : null;
							fun.setParameters(
								parmStrings != null ? stream(parmStrings).map(
									parm -> {
										int x;
										for (x = parm.length()-1; x >= 0 && BufferedScanner.isWordPart(parm.charAt(x)); x--) { }
										final String pname = parm.substring(x+1);
										final String type = parm.substring(0, x+1).trim();
										return new Variable(pname, PrimitiveType.fromCPPString(type));
									}
								).toArray(length -> new Variable[length]) : new Variable[0]
							);
							importsContainer.addDeclaration(fun);
						}
					}
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Missing source file " + sourceFilePath);
		}
	}
}
