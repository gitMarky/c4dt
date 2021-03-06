package net.arctics.clonk.c4script.ast;

import static net.arctics.clonk.util.Utilities.eq;

import org.eclipse.jface.text.Region;

import net.arctics.clonk.Core;
import net.arctics.clonk.ast.ASTNode;
import net.arctics.clonk.ast.ASTNodePrinter;
import net.arctics.clonk.ast.ControlFlowException;
import net.arctics.clonk.ast.EntityRegion;
import net.arctics.clonk.ast.ExpressionLocator;
import net.arctics.clonk.ast.IEvaluationContext;
import net.arctics.clonk.ast.Sequence;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.index.ID;

/**
 * Either '->' or '.' operator.
 * @author madeen
 *
 */
public class MemberOperator extends ASTNode implements ITidyable {

	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;
	boolean dotNotation;
	private final boolean hasTilde;
	private final ID id;
	private int idOffset;

	public ID id() { return id; }
	public boolean dotNotation() { return dotNotation; }
	public boolean hasTilde() { return hasTilde; }

	@Override
	protected void offsetExprRegion(final int amount, final boolean start, final boolean end) {
		super.offsetExprRegion(amount, start, end);
		if (start) {
			idOffset += amount;
		}
	}

	/**
	 * Helper method to test whether some arbitrary expression ends with a MemberOperator in dot notation.
	 * @param expression The {@link ASTNode} to test
	 * @return True if the expression represents something like (CreateObject(Clonk)->GetContainer().), false if not.
	 */
	public static boolean endsWithDot(final ASTNode expression) {
		return
			expression instanceof Sequence &&
			((Sequence)expression).lastElement() instanceof MemberOperator &&
			((MemberOperator)((Sequence)expression).lastElement()).dotNotation;
	}

	/**
	 * Construct a new MemberOperator.
	 * @param dotNotation If true, the operator represents a '.', otherwise '->'
	 * @param hasTilde Whether '->' is followed by '~'
	 * @param id {@link ID} specified after '->'. Can be null.
	 * @param idOffset Relative offset of id, used for correct hyperlink detection (see {@link #entityAt(int, ExpressionLocator)})
	 */
	public MemberOperator(final boolean dotNotation, final boolean hasTilde, final ID id, final int idOffset) {
		super();
		this.dotNotation = dotNotation;
		this.hasTilde = hasTilde;
		this.id = id;
		this.idOffset = idOffset;
	}

	public static MemberOperator makeDotOperator() { return new MemberOperator(true, false, null, 0); }

	@Override
	public void doPrint(final ASTNodePrinter output, final int depth) {
		if (dotNotation) {
			// so simple
			output.append('.');
		} else {
			if (hasTilde) {
				output.append("->~"); //$NON-NLS-1$
			}
			else {
				output.append("->"); //$NON-NLS-1$
			}
			if (id != null) {
				output.append(id.stringValue());
				output.append("::"); //$NON-NLS-1$
			}
		}
	}

	/**
	 * MemberOperators are never valid when not preceded by another {@link ASTNode}
	 */
	@Override
	public boolean isValidInSequence(final ASTNode predecessor) {
		return
			predecessor instanceof AccessDeclaration ||
			predecessor instanceof This ||
			predecessor instanceof ArrayElementExpression ||
			predecessor instanceof Parenthesized ||
			predecessor instanceof IDLiteral;
	}
	@Override
	public boolean isValidAtEndOfSequence() { return false; }

	@Override
	public EntityRegion entityAt(final int offset, final ExpressionLocator<?> locator) {
		if (id != null && offset >= idOffset && offset < idOffset+4) {
			return new EntityRegion(parent(Script.class).nearestDefinitionWithId(id), new Region(start()+idOffset, 4));
		}
		return null;
	}

	@Override
	public boolean equalAttributes(final ASTNode other) {
		if (!super.equalAttributes(other)) {
			return false;
		}
		final MemberOperator otherOp = (MemberOperator) other;
		if (dotNotation != otherOp.dotNotation || hasTilde != otherOp.hasTilde || !eq(id, otherOp.id)) {
			return false;
		}
		return true;
	}

	public ASTNode successorInSequence() {
		if (parent() instanceof Sequence) {
			return ((Sequence)parent()).successorOfSubElement(this);
		} else {
			return null;
		}
	}

	@Override
	public ASTNode tidy(final Tidy tidy) throws CloneNotSupportedException {
		if (!dotNotation && !tidy.structure().engine().settings().supportsProplists) {
			final ASTNode succ = successorInSequence();
			if (succ instanceof AccessVar) {
				return makeDotOperator();
			}
		}
		return this;
	}

	public static boolean unforgiving(final ASTNode p) {
		return p instanceof MemberOperator && !((MemberOperator)p).hasTilde();
	}

	@Override
	public Object evaluate(final IEvaluationContext context) throws ControlFlowException {
		return predecessor().evaluate(context);
	}

}