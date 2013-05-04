package net.arctics.clonk.c4script;

import static net.arctics.clonk.util.ArrayUtil.concat;
import static net.arctics.clonk.util.ArrayUtil.map;
import static net.arctics.clonk.util.Utilities.as;
import static net.arctics.clonk.util.Utilities.defaulting;
import static net.arctics.clonk.util.Utilities.eq;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.AppendableBackedExprWriter;
import net.arctics.clonk.ast.ControlFlowException;
import net.arctics.clonk.ast.DeclMask;
import net.arctics.clonk.ast.Declaration;
import net.arctics.clonk.ast.IASTSection;
import net.arctics.clonk.ast.IASTVisitor;
import net.arctics.clonk.ast.IEvaluationContext;
import net.arctics.clonk.ast.SourceLocation;
import net.arctics.clonk.ast.Structure;
import net.arctics.clonk.ast.TraversalContinuation;
import net.arctics.clonk.builder.ProjectSettings.Typing;
import net.arctics.clonk.c4script.Variable.Scope;
import net.arctics.clonk.c4script.ast.AccessVar;
import net.arctics.clonk.c4script.ast.FunctionBody;
import net.arctics.clonk.c4script.ast.ReturnException;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Engine;
import net.arctics.clonk.index.IIndexEntity;
import net.arctics.clonk.index.Index;
import net.arctics.clonk.util.IConverter;
import net.arctics.clonk.util.IHasUserDescription;
import net.arctics.clonk.util.StringUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;

/**
 * A function in a script.
 * @author ZokRadonh
 *
 */
public class Function extends Structure implements Serializable, ITypeable, IHasUserDescription, IEvaluationContext, IHasCode, IASTSection {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	private FunctionScope visibility;
	private List<Variable> localVars;
	protected List<Variable> parameters;
	private IType returnType;
	private String description;
	private String returnDescription;
	private boolean isCallback;
	private boolean isOldStyle;
	private boolean staticallyTyped;
	private boolean typeFromCallsHint;
	private SourceLocation bodyLocation, header;
	private int nameStart;

	/**
	 * Code block kept in memory for speed optimization
	 */
	private FunctionBody body;

	/**
	 * Hash code of the string the block was parsed from.
	 */
	private int blockSourceHash;
	
	private int totalNumASTNodes;
	
	public int totalNumASTNodes() { return totalNumASTNodes; }

	/**
	 * Create a new function.
	 * @param name Name of the function
	 * @param returnType Return type of the function
	 * @param pars Parameter variables to add to the parameter list of the function
	 */
	public Function(String name, IType returnType, Variable... pars) {
		this.name = name;
		this.returnType = returnType;
		parameters = new ArrayList<Variable>(pars.length);
		for (final Variable var : pars) {
			parameters.add(var);
			var.setParent(this);
		}
		visibility = FunctionScope.GLOBAL;
	}

	public Function() {
		visibility = FunctionScope.GLOBAL;
		name = ""; //$NON-NLS-1$
		clearParameters();
		localVars = new ArrayList<Variable>();
	}

	public Function(String name, Script parent, FunctionScope scope) {
		this.name = name;
		visibility = scope;
		clearParameters();
		localVars = new ArrayList<Variable>();
		setParent(parent);
	}

	public Function(String name, Definition parent, String scope) {
		this(name,parent,FunctionScope.makeScope(scope));
	}

	public Function(String name, FunctionScope scope) {
		this(name, null, scope);
	}

	/**
	 * @return the localVars
	 */
	public List<Variable> locals() { return localVars; }

	/**
	 * @return the parameter
	 */
	public List<Variable> parameters() { return parameters; }

	public boolean typeFromCallsHint() { return typeFromCallsHint; 	}
	public void setTypeFromCallsHint(boolean value) {
		this.typeFromCallsHint = value;
	}

	public Variable addParameter(Variable parameter) {
		synchronized (parameters) {
			parameters.add(parameter);
			return parameter;
		}
	}

	public void clearParameters() { parameters = new ArrayList<Variable>(); }

	public Variable parameter(int index) {
		synchronized (parameters) {
			return index >= 0 && index < parameters.size() ? parameters.get(index) : null;
		}
	}

	public int numParameters() {
		synchronized (parameters) {
			return parameters.size();
		}
	}

	/**
	 * @param parameters the parameter to set
	 */
	public void setParameters(List<Variable> parameters) {
		this.parameters = parameters;
	}

	/**
	 * @return the returnType
	 */
	public IType returnType() {
		if (returnType == null)
			returnType = PrimitiveType.UNKNOWN;
		return returnType;
	}

	public IType returnType(Script script) {
		IType retType = null;
		if (script != null && script.typings().functionReturnTypes != null)
			retType = script.typings().functionReturnTypes.get(this.name());
		if (retType == null)
			retType = returnType();
		return retType;
	}

	/**
	 * @return the visibility
	 */
	public FunctionScope visibility() { return visibility; }
	/**
	 * @param visibility the visibility to set
	 */
	public void setVisibility(FunctionScope visibility) { this.visibility = visibility; }
	/**
	 * @return the description
	 */
	@Override
	public String obtainUserDescription() { return description; }
	@Override
	public String userDescription() { return description; }
	/**
	 * @param description the description to set
	 */
	@Override
	public void setUserDescription(String description) { this.description = description; }
	public String returnDescription() { return returnDescription; }
	public void setReturnDescription(String returnDescription) { this.returnDescription = returnDescription; }

	public final class FunctionInvocation implements IEvaluationContext {
		private final Object[] args;
		private final IEvaluationContext up;
		private final Object context;
		public FunctionInvocation(Object[] args, IEvaluationContext up, Object context) {
			this.args = args;
			this.up = up;
			this.context = context;
		}
		@Override
		public Object valueForVariable(AccessVar access) {
			int i = 0;
			if (access.predecessorInSequence() == null)
				for (final Variable v : parameters) {
					if (v.name().equals(access.name()))
						return args[i];
					i++;
				}
			return up != null ? up.valueForVariable(access) : null;
		}
		@Override
		public Script script() { return Function.this.script(); }
		@Override
		public void reportOriginForExpression(ASTNode expression, IRegion location, IFile file) {
			Function.this.reportOriginForExpression(expression, location, file);
		}
		@Override
		public Function function() { return Function.this; }
		@Override
		public int codeFragmentOffset() { return Function.this.codeFragmentOffset(); }
		@Override
		public Object[] arguments() { return args; }
		@Override
		public Object cookie() { return context; }
	}

	/**
	 * The scope of a function.
	 * @author ZokRadonh
	 *
	 */
	public enum FunctionScope {
		GLOBAL,
		PUBLIC,
		PROTECTED,
		PRIVATE;

		private static final Map<String, FunctionScope> scopeMap = new HashMap<String, FunctionScope>();
		static {
			for (final FunctionScope s : values())
				scopeMap.put(s.name().toLowerCase(), s);
		}

		private String lowerCaseName;

		public static FunctionScope makeScope(String scopeString) {
			return scopeMap.get(scopeString);
		}

		@Override
		public String toString() {
			if (lowerCaseName == null)
				lowerCaseName = this.name().toLowerCase();
			return lowerCaseName;
		}

		public Object toKeyword() {
			return toString();
		}
	}

	/**
	 * Options for {@link Function#parameterString(EnumSet)}
	 * @author madeen
	 */
	public enum ParameterStringOption {
		/** Include function name in printed string */
		FunctionName,
		/** Print string that the engine will be able to parse (no special typing constructs or 'any') */
		EngineCompatible,
		/** Include comments of parameters */
		ParameterComments
	}

	/**
	 * Print parameter string using options.
	 * @param options Options
	 * @return The printed string.
	 */
	public String parameterString(EnumSet<ParameterStringOption> options) {
		final StringBuilder string = new StringBuilder();
		if (options.contains(ParameterStringOption.FunctionName)) {
			string.append(name());
			string.append("("); //$NON-NLS-1$
		}
		printParameterString(new AppendableBackedExprWriter(string), options);
		if (options.contains(ParameterStringOption.FunctionName))
			string.append(")"); //$NON-NLS-1$
		return string.toString();
	}

	@Override
	public String displayString(IIndexEntity context) {
		return parameterString(EnumSet.of(ParameterStringOption.FunctionName, ParameterStringOption.EngineCompatible));
	}

	private void printParameterString(ASTNodePrinter output, final EnumSet<ParameterStringOption> options) {
		if (numParameters() > 0)
			StringUtil.writeBlock(output, "", "", ", ", map(parameters(), new IConverter<Variable, String>() {
				final boolean engineCompatible = options.contains(ParameterStringOption.EngineCompatible);
				@Override
				public String convert(Variable par) {
					final IType type = engineCompatible ?
						(par.staticallyTyped() ? par.type().simpleType() : PrimitiveType.ANY) : par.type();
					if (engineCompatible && !par.isActualParm())
						return null;
					final String comment = par.userDescription() != null && options.contains(ParameterStringOption.ParameterComments) ? ("/* " + par.userDescription() + "*/ ") : "";
					boolean includeType;
					if (engineCompatible && (type == PrimitiveType.ANY || type == PrimitiveType.UNKNOWN))
						includeType = false;
					else
						includeType = true;
					return comment + (includeType ? (type.typeName(!engineCompatible) + " ") : "") + par.name();
				}
			}));
	}

	/**
	 * @param isCallback the isCallback to set
	 */
	public void setCallback(boolean isCallback) {
		this.isCallback = isCallback;
	}

	/**
	 * @return the isCallback
	 */
	public boolean isCallback() {
		return isCallback;
	}

	/**
	 * Set the body location.
	 * @param body the body location to set
	 */
	public void setBodyLocation(SourceLocation body) {
		this.bodyLocation = body;
	}

	/**
	 * @return the body the location.
	 */
	public SourceLocation bodyLocation() {
		return bodyLocation;
	}

	public SourceLocation wholeBody() {
		if (bodyLocation == null)
			return null;
		if (isOldStyle())
			return bodyLocation;
		else
			return new SourceLocation(bodyLocation.start()-1, bodyLocation.end()+1);
	}

	@Override
	public int sortCategory() {
		return Variable.Scope.values().length + visibility.ordinal();
	}

	public static String documentationURLForFunction(String functionName, Engine engine) {
		return engine.settings().documentationURLForFunction(functionName);
	}

	/**
	 * For engine functions: Return URL string for documentation
	 * @return The documentation URl
	 */
	public String documentationURL() {
		return documentationURLForFunction(name(), script().engine());
	}

	@Override
	public String infoText(IIndexEntity context) {
		final String description = obtainUserDescription();
		final StringBuilder builder = new StringBuilder();
		final String scriptPath = script().resource() != null
			? script().resource().getProjectRelativePath().toOSString()
			: script().name();
		for (final String line : new String[] {
			MessageFormat.format("<i>{0}</i><br/>", scriptPath), //$NON-NLS-1$ //$NON-NLS-2$
			MessageFormat.format("<b>{0}</b><br/>", parameterString(EnumSet.of(ParameterStringOption.FunctionName, ParameterStringOption.EngineCompatible))), //$NON-NLS-1$ //$NON-NLS-2$
			"<br/>", //$NON-NLS-1$
			description != null && !description.equals("") ? description : Messages.DescriptionNotAvailable, //$NON-NLS-1$
			"<br/>", //$NON-NLS-1$
		})
			builder.append(line);
		if (numParameters() > 0) {
			builder.append(MessageFormat.format("<br/><b>{0}</b><br/>", Messages.Parameters)); //$NON-NLS-1$ //$NON-NLS-3$
			for (final Variable p : parameters())
				builder.append(MessageFormat.format("<b>{0} {1}</b> {2}<br/>", StringUtil.htmlerize(p.type().typeName(true)), p.name(), p.userDescription())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			builder.append("<br/>"); //$NON-NLS-1$
		}
		final IType retType = returnType(as(context, Script.class));

		if (retType != PrimitiveType.UNKNOWN) {
			builder.append(MessageFormat.format("<br/><b>{0} </b>{1}<br/>", //$NON-NLS-1$
				Messages.Returns,
				StringUtil.htmlerize(retType.typeName(true))));
			if (returnDescription != null)
				builder.append(returnDescription+"<br/>");
		}
		return builder.toString();
	}

	@Override
	public Variable findDeclaration(String declarationName, Class<? extends Declaration> declarationClass) {
		return findLocalDeclaration(declarationName, declarationClass);
	}

	@Override
	public Variable findLocalDeclaration(String declarationName, Class<? extends Declaration> declarationClass) {
		if (declarationClass.isAssignableFrom(Variable.class)) {
			if (declarationName.equals(Variable.THIS.name()))
				return Variable.THIS;
			for (final Variable v : localVars)
				if (v.name().equals(declarationName))
					return v;
			for (final Variable p : parameters)
				if (p.name().equals(declarationName))
					return p;
		}
		return null;
	}

	public Variable findParameter(String parameterName) {
		for (final Variable p : parameters())
			if (p.name().equals(parameterName))
				return p;
		return null;
	}

	public Variable findVariable(String variableName) {
		return findDeclaration(variableName, Variable.class);
	}

	public boolean isOldStyle() {
		return isOldStyle;
	}

	public void setOldStyle(boolean isOldStyle) {
		this.isOldStyle = isOldStyle;
	}

	private transient Function cachedInherited;

	private static final Function NO_FUNCTION = new Function();

	/**
	 * Return the function this one inherits from
	 * @return The inherited function
	 */
	public Function inheritedFunction() {
		return cachedInherited == NO_FUNCTION ? null : cachedInherited;
	}

	public final void findInherited() {
		cachedInherited = defaulting(internalInherited(), NO_FUNCTION);
	}

	private Function internalInherited() {
		// search in #included scripts
		final Collection<Script> includesCollection = script().includes(0);
		final Script[] includes = includesCollection.toArray(new Script[includesCollection.size()]);
		for (int i = includes.length-1; i >= 0; i--) {
			final Function fun = includes[i].findFunction(name());
			if (fun != null && fun != this)
				return fun;
		}

		// search global
		final Function global = index().findGlobal(Function.class, name());
		if (global != null && global != this)
			return global;

		// search in engine
		final Function f = index().engine().findFunction(name());
		if (f != null)
			return f;

		return null;
	}

	/**
	 * Return the first function in the inherited chain.
	 * @return The first function in the inherited chain.
	 */
	public Function baseFunction() {
		Function result = this;
		final Set<Function> alreadyVisited = new HashSet<Function>();
		for (Function f = this; f != null; f = f.inheritedFunction()) {
			if (!alreadyVisited.add(f))
				break;
			result = f;
		}
		return result;
	}

	@Override
	public Object[] subDeclarationsForOutline() {
		return locals().toArray();
	}

	/**
	 * Create num generically named parameters (par1, par2, ...)
	 * @param num Number of parameters to create
	 */
	public void createParameters(int num) {
		for (int i = parameters.size(); i < num; i++)
			parameters.add(new Variable("par"+i, Scope.VAR)); //$NON-NLS-1$
	}

	/**
	 * Return the location of the function header
	 * @return
	 */
	public SourceLocation header() { return header; }
	/**
	 * Set the location of the function header.
	 * @param header
	 */
	public void setHeader(SourceLocation header) { this.header = header; }

	public void setNameStart(int start) { this.nameStart = start; }
	@Override
	public int nameStart() { return nameStart; }

	/**
	 * Print the function header into the passed string builder
	 * @param output The StringBuilder to add the header string to
	 */
	public void printHeader(ASTNodePrinter output) {
		printHeader(output, isOldStyle());
	}

	/**
	 * Print the function header into the passed string builder
	 * @param output The StringBuilder to add the header string to
	 * @param oldStyle Whether to print in old 'label-style'
	 */
	public void printHeader(ASTNodePrinter output, boolean oldStyle) {
		if (engine().settings().supportsFunctionVisibility) {
			output.append(visibility().toString());
			output.append(" "); //$NON-NLS-1$
		}
		if (!oldStyle) {
			output.append(Keywords.Func);
			final Typing typing = typing();
			switch (typing) {
			case DYNAMIC:
				break;
			case PARAMETERS_OPTIONALLY_TYPED:
				if (eq(returnType, PrimitiveType.REFERENCE))
					output.append(" &");
				break;
			case STATIC:
				output.append(" ");
				output.append(returnType.typeName(false));
				break;
			}
			output.append(" "); //$NON-NLS-1$
		}
		output.append(name());
		if (!oldStyle) {
			output.append("("); //$NON-NLS-1$
			printParameterString(output, EnumSet.of(ParameterStringOption.FunctionName, ParameterStringOption.EngineCompatible));
			output.append(")"); //$NON-NLS-1$
		}
		else
			output.append(":"); //$NON-NLS-1$
	}

	/**
	 * Return the header string
	 * @param oldStyle Whether to return the header string in old 'label-style'
	 * @return The header string
	 */
	public String headerString(boolean oldStyle) {
		final StringBuilder builder = new StringBuilder();
		printHeader(new AppendableBackedExprWriter(builder), oldStyle);
		return builder.toString();
	}

	/*+
	 * Return the header string
	 */
	public String headerString() {
		return headerString(isOldStyle());
	}

	@Override
	public IType type() {
		return returnType();
	}

	@Override
	public void forceType(IType type) {
		this.returnType = type;
		this.staticallyTyped = true;
	}

	@Override
	public void assignType(IType returnType, boolean _static) {
		if (!staticallyTyped || _static) {
			this.returnType = returnType;
			this.staticallyTyped = _static;
		}
	}

	public void setObjectType(Definition object) {
		//expectedContent = object;
	}

	/**
	 * Returns whether this function inherits from the calling function
	 * @param otherFunc
	 * @param recursionCatcher Recursion catcher!
	 * @return true if related, false if not
	 */
	private boolean inheritsFrom(Function otherFunc, Set<Function> recursionCatcher) {
		Function f = this;
		while (f != null && !recursionCatcher.contains(f)) {
			recursionCatcher.add(f);
			if (otherFunc == f)
				return true;
			f = f.inheritedFunction();
		}
		return false;
	}

	/**
	 * Returns whether the function passed to this method is in the same override line as the calling function
	 * @param otherFunc
	 * @return true if both functions are related, false if not
	 */
	public boolean isRelatedFunction(Function otherFunc) {
		final Set<Function> recursionCatcher = new HashSet<Function>();
		if (this.inheritsFrom(otherFunc, recursionCatcher))
			return true;
		Function f = this;
		while (f != null && !recursionCatcher.contains(f)) {
			recursionCatcher.add(f);
			if (otherFunc.inheritsFrom(f, recursionCatcher))
				return true;
			f = f.inheritedFunction();
		}
		return false;
	}

	@Override
	public List<Declaration> subDeclarations(Index contextIndex, int mask) {
		final ArrayList<Declaration> decs = new ArrayList<Declaration>();
		if ((mask & DeclMask.VARIABLES) != 0) {
			decs.addAll(localVars);
			decs.addAll(parameters);
		}
		return decs;
	}

	@Override
	public boolean isGlobal() {
		return visibility() == FunctionScope.GLOBAL;
	}

	/**
	 * Return whether num parameters are more than needed for this function
	 * @param num Number of parameters to test for
	 * @return See above
	 */
	public boolean tooManyParameters(int num) {
		synchronized (parameters) {
			return
				(parameters.size() == 0 || parameters.get(parameters.size()-1).isActualParm()) &&
				num > parameters.size();
		}
	}

	/**
	 * Invoke this function using a crude AST interpreter.
	 * @param context Context object
	 * @return the result
	 */
	public Object invoke(IEvaluationContext context) {
		try {
			return body != null ? body.evaluate(context) : null;
		} catch (final ReturnException result) {
			return result.result();
		} catch (final ControlFlowException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Remove local variables.
	 */
	public void clearLocalVars() {
		localVars.clear();
	}

	@Override
	public boolean staticallyTyped() {
		return staticallyTyped;
	}

	public void resetLocalVarTypes() {
		for (final Variable v : locals())
			v.forceType(PrimitiveType.UNKNOWN);
	}
	
	private static final IASTVisitor<Function> AST_ASSIGN_IDENTIFIER_VISITOR = new IASTVisitor<Function>() {
		@Override
		public TraversalContinuation visitNode(ASTNode node, Function context) {
			if (node != null)
				node.localIdentifier(context.totalNumASTNodes++);
			return TraversalContinuation.Continue;
		}
	};
	
	public void storeBody(ASTNode block, String source) {
		body = (FunctionBody)block;
		blockSourceHash = source.hashCode();
		if (bodyLocation != null)
			body.setLocation(0, bodyLocation.getLength());
		totalNumASTNodes = 0;
		body.traverse(AST_ASSIGN_IDENTIFIER_VISITOR, this);
		body.setParent(this);
	}

	/**
	 * Return cached code block if it was created from the given source. This is tested by hash code of the source string.
	 * @param source The source to test against
	 * @return The code block or null if it was created from differing source.
	 */
	public FunctionBody bodyMatchingSource(String source) {
		if (source == null || (blockSourceHash != -1 && blockSourceHash == source.hashCode()))
			//if (body != null)
			//	body.postLoad(this, TypeUtil.problemReportingContext(this));
			return body;
		else
			return body = null;
	}

	/**
	 * Return the cached block without performing checks.
	 * @return The cached code block
	 */
	public FunctionBody body() {
		return bodyMatchingSource(null);
	}

	@Override
	public <T extends Declaration> T latestVersionOf(T from) {
		if (from instanceof Variable)
			return super.latestVersionOf(from);
		else
			return null;
	}

	/**
	 * Assign parameter types to existing parameters. If more types than parameters are given, no new parameters will be created.
	 * @param types The types to assign to the parameters
	 */
	public void assignParameterTypes(IType... types) {
		if (types == null)
			return;
		synchronized (parameters) {
			for (int i = 0; i < types.length; i++) {
				if (i >= parameters.size())
					break;
				parameters.get(i).forceType(types[i], true);
			}
		}
	}

	@Override
	public int absoluteExpressionsOffset() {
		return bodyLocation().getOffset();
	}

	@Override
	public Object valueForVariable(AccessVar access) {
		if (access.predecessorInSequence() == null)
			return findVariable(access.name()); // return meta object instead of concrete value
		else
			return null;
	}

	@Override
	public Object[] arguments() {
		synchronized (parameters) {
			return parameters.toArray();
		}
	}

	@Override
	public Object cookie() { return null; }
	@Override
	public Function function() { return this; }
	@Override
	public void reportOriginForExpression(ASTNode expression, IRegion location, IFile file) { /* oh interesting */ }
	@Override
	public int codeFragmentOffset() { return bodyLocation != null ? bodyLocation.getOffset() : 0; }
	@Override
	public boolean isLocal() { return true; }
	@Override
	public ASTNode code() { return body(); }

	public static String scaffoldTextRepresentation(String functionName, FunctionScope scope, final Index context, Variable... parameters) {
		final StringBuilder builder = new StringBuilder();
		@SuppressWarnings("serial")
		final Function f = new Function(functionName, scope) {
			@Override
			protected Typing typing() { return context.nature().settings().typing; }
			@Override
			public Engine engine() { return context.engine(); }
		};
		f.assignType(PrimitiveType.ANY, true);
		for (final Variable p : parameters)
			f.addParameter(p);
		final ASTNodePrinter printer = new AppendableBackedExprWriter(builder);
		f.printHeader(printer);
		Conf.blockPrelude(printer, 0);
		builder.append("{\n"); //$NON-NLS-1$
		builder.append(Conf.indentString);
		builder.append("\n}"); //$NON-NLS-1$
		return builder.toString();
	}

	@Override
	public ASTNode[] subElements() {
		return concat(super.subElements(), body());
	}
	@Override
	public void setSubElements(ASTNode[] elms) { storeBody(elms[0], ""); }

	@Override
	public void doPrint(ASTNodePrinter output, int depth) {
		printHeader(output);
		Conf.blockPrelude(output, depth);
		body.print(output, depth);
	}

	@Override
	public int absoluteOffset() { return bodyLocation().start(); }

}