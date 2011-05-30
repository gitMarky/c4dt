package net.arctics.clonk.parser.c4script.ast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.Declaration;
import net.arctics.clonk.parser.ParserErrorCode;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.c4script.DeclarationObtainmentContext;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.Variable;
import net.arctics.clonk.parser.c4script.IType;
import net.arctics.clonk.parser.c4script.ProplistDeclaration;
import net.arctics.clonk.parser.c4script.ast.evaluate.IEvaluationContext;
import net.arctics.clonk.parser.inireader.IniData.IniConfiguration;

public class PropListExpression extends Value {

	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;
	
	private ProplistDeclaration definedDeclaration;
	
	public ProplistDeclaration getDefinedDeclaration() {
		return definedDeclaration;
	}
	
	public List<Variable> getComponents() {
		return definedDeclaration.getComponents();
	}
	
	public PropListExpression(ProplistDeclaration declaration) {
		this.definedDeclaration = declaration;
		assignParentToSubElements();
	}
	@Override
	public void doPrint(ExprWriter output, int depth) {
		output.append('{');
		List<Variable> components = getComponents();
		for (int i = 0; i < components.size(); i++) {
			Variable component = components.get(i);
			output.append('\n');
			Conf.printIndent(output, depth-1);
			output.append(component.getName());
			output.append(": "); //$NON-NLS-1$
			component.getInitializationExpression().print(output, depth+1);
			if (i < components.size()-1) {
				output.append(',');
			} else {
				output.append('\n'); Conf.printIndent(output, depth-2);
			}
		}
		output.append('}');
	}
	@Override
	protected IType obtainType(DeclarationObtainmentContext parser) {
		return definedDeclaration;
	}
	@Override
	public boolean modifiable(C4ScriptParser parser) {
		return false;
	}
	@Override
	public boolean isValidInSequence(ExprElm predecessor, C4ScriptParser parser) {
		return predecessor == null;
	}
	@Override
	public ExprElm[] getSubElements() {
		List<Variable> components = getComponents();
		ExprElm[] result = new ExprElm[components.size()];
		for (int i = 0; i < result.length; i++)
			result[i] = components.get(i).getInitializationExpression();
		return result;
	}
	@Override
	public void setSubElements(ExprElm[] elms) {
		List<Variable> components = getComponents();
		for (int i = 0; i < Math.min(elms.length, components.size()); i++) {
			components.get(i).setInitializationExpression(elms[i]);
		}
	}
	@Override
	public boolean isConstant() {
		// whoohoo, proplist expressions can be constant if all components are constant
		for (Variable component : getComponents()) {
			if (!component.getInitializationExpression().isConstant())
				return false;
		}
		return true;
	}
	
	@Override
	public Object evaluateAtParseTime(IEvaluationContext context) {
		List<Variable> components = getComponents();
		Map<String, Object> map = new HashMap<String, Object>(components.size());
		for (Variable component : components) {
			map.put(component.getName(), component.getInitializationExpression().evaluateAtParseTime(context));
		}
		return map;
	}
	
	public IniConfiguration guessedConfiguration(C4ScriptParser context) {
		if (context.getCurrentVariable() != null) {
			return context.getContainer().getEngine().getIniConfigurations().getConfigurationFor(context.getCurrentVariable().getName()+".txt"); //$NON-NLS-1$
		} else {
			return null;
		}
	}
	
	private Declaration associatedDeclaration;
	public Declaration getAssociatedDeclaration() {
		return associatedDeclaration;
	}
	@Override
	public void setAssociatedDeclaration(Declaration declaration) {
		associatedDeclaration = declaration;
	}
	
	public ExprElm value(String key) {
		Variable keyVar = definedDeclaration.findComponent(key);
		return keyVar != null ? keyVar.getInitializationExpression() : null;
	} 
	
	@SuppressWarnings("unchecked")
	public <T> T valueEvaluated(String key, Class<T> cls) {
		ExprElm e = value(key);
		if (e != null) {
			Object eval = e.evaluateAtParseTime(definedDeclaration.getParentDeclarationOfType(IEvaluationContext.class));
			return eval != null && cls.isAssignableFrom(eval.getClass()) ? (T)eval : null;
		} else
			return null;
	}
	
	@Override
	public void reportErrors(C4ScriptParser parser) throws ParsingException {
		super.reportErrors(parser);
		if (!parser.getContainer().getEngine().getCurrentSettings().proplistsSupported)
			parser.errorWithCode(ParserErrorCode.NotSupported, this, C4ScriptParser.NO_THROW, Messages.PropListExpression_ProplistsFeature);
	}
}