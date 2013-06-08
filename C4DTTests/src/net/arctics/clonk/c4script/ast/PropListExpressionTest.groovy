package net.arctics.clonk.c4script.ast;

import static org.junit.Assert.*;
import net.arctics.clonk.c4script.ProplistDeclaration
import net.arctics.clonk.c4script.Variable
import org.junit.Test;

public class PropListExpressionTest {
	@Test
	public void testLongPropListParameterPrinting() {
		PropListExpression expr = new PropListExpression(new ProplistDeclaration([
			new Variable("longIdentifierIsLong", new IntegerLiteral(Integer.MAX_VALUE)),
			new Variable("anotherOneIsEvenLonger", new StringLiteral("asdasdasdasdasdasdasdasdasd"))
		]));
		CallDeclaration call = new CallDeclaration("SomeFunction", expr);
		assertEquals(
"""SomeFunction
(
	{
		longIdentifierIsLong: 2147483647,
		anotherOneIsEvenLonger: "asdasdasdasdasdasdasdasdasd"
	}
)""", call.printed());
	}
}