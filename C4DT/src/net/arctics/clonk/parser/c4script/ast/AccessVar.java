package net.arctics.clonk.parser.c4script.ast;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.parser.C4Declaration;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.PrimitiveType;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.FindDeclarationInfo;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.Function.C4FunctionScope;
import net.arctics.clonk.parser.c4script.Variable.C4VariableScope;
import net.arctics.clonk.parser.c4script.ProplistDeclaration;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;

public class AccessVar extends AccessDeclaration {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	@Override
	public boolean modifiable(C4ScriptParser context) {
		ExprElm pred = getPredecessorInSequence();
		if (pred == null) {
			return declaration == null || ((Variable)declaration).getScope() != C4VariableScope.CONST;
		} else {
			return true; // you can never be so sure 
		}
	}

	public AccessVar(String varName) {
		super(varName);
	}

	public AccessVar(C4Declaration declaration) {
		this(declaration.getName());
		this.declaration = declaration;
	}

	@Override
	public boolean isValidInSequence(ExprElm predecessor, C4ScriptParser context) {
		return
			// either null or
			predecessor == null ||
			// normally, a check would be performed whether the MemberOperator uses '.' instead of '->'
			// but in order to avoid the case where writing obj->StartOfFuncName is interpreted as
			// two not-properly-finished statements (obj->; and StartOfFuncName;) and then having
			// the StartOfFuncName; statement be replaced by the function call which will then have no
			// parameter information since the function only exists in the definition of obj that is not
			// consulted in that case, the '.' rule is enforced not here but in reportErrors (!!1)
			predecessor instanceof MemberOperator;
	}

	@Override
	public C4Declaration obtainDeclaration(C4ScriptParser parser) {
		ExprElm p = getPredecessorInSequence();
		ScriptBase scriptToLookIn = null;
		if (p != null) {
			IType type = p.getType(parser);
			if ((scriptToLookIn = Definition.scriptFrom(type)) == null) {
				// find pseudo-variable from proplist expression
				if (type instanceof ProplistDeclaration) {
					return ((ProplistDeclaration)type).findComponent(getDeclarationName());
				}
			}
		} else {
			scriptToLookIn = parser.getContainer();
		}
		if (scriptToLookIn != null) {
			FindDeclarationInfo info = new FindDeclarationInfo(parser.getContainer().getIndex());
			info.setContextFunction(parser.getCurrentFunc());
			info.setSearchOrigin(scriptToLookIn);
			return scriptToLookIn.findVariable(declarationName, info);
		} else {
			return null;
		}
	}

	@Override
	public void reportErrors(C4ScriptParser parser) throws ParsingException {
		super.reportErrors(parser);
		ExprElm pred = getPredecessorInSequence();
		if (declaration == null && pred == null) {
			parser.errorWithCode(ParserErrorCode.UndeclaredIdentifier, this, true, declarationName);
		}
		// local variable used in global function
		else if (declaration instanceof Variable) {
			Variable var = (Variable) declaration;
			var.setUsed(true);
			switch (var.getScope()) {
				case LOCAL:
					C4Declaration d = parser.getCurrentDeclaration();
					if (d != null) {
						Function f = d.getTopLevelParentDeclarationOfType(Function.class);
						Variable v = d.getTopLevelParentDeclarationOfType(Variable.class);
						if (
							(f != null && f.getVisibility() == C4FunctionScope.GLOBAL) ||
							(f == null && v != null && v.getScope() != C4VariableScope.LOCAL)
						) {
							parser.errorWithCode(ParserErrorCode.LocalUsedInGlobal, this, true);
						}
					}
					break;
				case STATIC:
					parser.getContainer().addUsedProjectScript(var.getScript());
					break;
				case VAR:
					if (var.getLocation() != null && parser.getCurrentFunc() != null && var.getFunction() == parser.getCurrentFunc()) {
						int locationUsed = parser.getCurrentFunc().getBody().getOffset()+this.getExprStart();
						if (locationUsed < var.getLocation().getOffset())
							parser.warningWithCode(ParserErrorCode.VarUsedBeforeItsDeclaration, this, var.getName());
					}
					break;
			}
		}
		if (pred != null && pred instanceof MemberOperator && !((MemberOperator)pred).dotNotation) {
			parser.errorWithCode(ParserErrorCode.DotNotationInsteadOfArrow, this, true, this.getDeclarationName());
		}
	}

	public static IStoredTypeInformation createStoredTypeInformation(C4Declaration declaration) {
		if (declaration != null) {
			return new GenericStoredTypeInformation(new AccessVar(declaration));
		} else {
			return null;
		}
	}
	
	@Override
	protected IType obtainType(C4ScriptParser context) {
		C4Declaration d = getDeclaration(context);
		// getDeclaration(context) ensures that declaration is not null (if there is actually a variable) which is needed for queryTypeOfExpression for example
		if (d == Variable.THIS)
			return context.getContainerObject() != null ? context.getContainerObject() : PrimitiveType.OBJECT;
		IType stored = context.queryTypeOfExpression(this, null);
		if (stored != null)
			return stored;
		if (d instanceof Variable) {
			Variable v = (Variable) d;
			if (v.getObjectType() != null)
				return v.getObjectType();
			else
				return v.getType();
		}
		return PrimitiveType.UNKNOWN;
	}

	@Override
	public void expectedToBeOfType(IType type, C4ScriptParser context, TypeExpectancyMode mode, ParserErrorCode errorWhenFailed) {
		if (getDeclaration() == Variable.THIS)
			return;
		super.expectedToBeOfType(type, context, mode, errorWhenFailed);
	}

	@Override
	public void inferTypeFromAssignment(ExprElm expression, C4ScriptParser context) {
		if (getDeclaration() == Variable.THIS)
			return;
		IType predType = getPredecessorInSequence() != null ? getPredecessorInSequence().getType(context) : null;
		if (predType instanceof ProplistDeclaration) {
			ProplistDeclaration proplDec = (ProplistDeclaration) predType;
			
			// FIXME: always ok to add to existing proplist declaration?
			Variable adhocVar = new Variable(getDeclarationName(), C4VariableScope.VAR);
			adhocVar.setLocation(context.absoluteSourceLocationFromExpr(this));
			adhocVar.forceType(expression.getType(context));
			adhocVar.setInitializationExpression(expression);
			adhocVar.setParentDeclaration(proplDec);
			declaration = adhocVar;
			proplDec.addComponent(adhocVar, true);
		}
		super.inferTypeFromAssignment(expression, context);
	}
	
	private static Definition getObjectBelongingToStaticVar(Variable var) {
		C4Declaration parent = var.getParentDeclaration();
		if (parent instanceof Definition && ((Definition)parent).getStaticVariable() == var)
			return (Definition) parent;
		else
			return null;
	}

	@Override
	public Object evaluateAtParseTime(ScriptBase context) {
		Definition obj;
		if (declaration instanceof Variable) {
			Variable var = (Variable) declaration;
			if (var.getScope() == C4VariableScope.CONST) {
				Object val = var.getDefaultValue();
				if (val == null)
					val = 1337; // awesome fallback
				return val;
			}
			else if ((obj = getObjectBelongingToStaticVar(var)) != null) {
				return obj.getId(); // just return the id
			}
		}
		return super.evaluateAtParseTime(context);
	}

	public boolean constCondition() {
		return declaration instanceof Variable && ((Variable)declaration).getScope() == C4VariableScope.CONST;
	}
	
	@Override
	public Object evaluate(IEvaluationContext context) throws ControlFlowException {
		if (context != null) {
			return context.getValueForVariable(getDeclarationName());
		}
		else {
			return super.evaluate(context);
		}
	}
	
	@Override
	public boolean isConstant() {
		if (getDeclaration() instanceof Variable) {
			Variable var = (Variable) getDeclaration();
			// naturally, consts are constant
			return var.getScope() == C4VariableScope.CONST || getObjectBelongingToStaticVar(var) != null;
		}
		else
			return false;
	}
	
	@Override
	public Class<? extends C4Declaration> declarationClass() {
		return Variable.class;
	}

}