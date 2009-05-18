package net.arctics.clonk.parser;

import net.arctics.clonk.parser.c4script.C4Directive;
import net.arctics.clonk.parser.c4script.C4ScriptParser.Keywords;
import net.arctics.clonk.parser.c4script.C4ScriptOperator;

public class BuiltInDefinitions {
	public static final String[] OBJECT_CALLBACKS = new String[] { "Activate", "ActivateEntrance", "AttachTargetLost", "BuildNeedsMaterial", "CalcBuyValue", "CalcDefValue", "CalcSellValue", "CalcValue", "CatchBlow", "Collection", "Collection2", "Completion", "Construction", "ContainedLeft", "ContainedRight", "ContainedUp", "ContainedDown", "ContainedDig", "ContainedThrow", "ContainedUpdate", "ContainedLeftSingle", "ContainedRightSingle", "ContainedUpSingle", "ContainedDownSingle", "ContainedDigSingle", "ContainedThrowSingle", "ContainedLeftDouble", "ContainedRightDouble", "ContainedUpDouble", "ContainedDownDouble", "ContainedDigDouble", "ContainedThrowDouble", "ControlCommand", "ControlCommandFinished", "ControlContents", "ControlTransfer", "ControlLeft", "ControlUp", "ControlRight", "ControlDown", "ControlDig", "ControlThrow", "ControlSpecial", "ControlWheelUp", "ControlWheelDown", "ControlLeftSingle", "ControlUpSingle", "ControlRightSingle", "ControlDownSingle", "ControlDigSingle", "ControlThrowSingle", "ControlSpecialSingle", "ControlLeftDouble", "ControlUpDouble", "ControlRightDouble", "ControlDownDouble", "ControlDigDouble", "ControlThrowDouble", "ControlSpecialDouble", "ControlLeftReleased", "ControlUpReleased", "ControlRightReleased", "ControlDownReleased", "ControlDigReleased", "ControlThrowReleased", "ControlUpdate", "CrewSelection", "Damage", "Death", "DeepBreath", "Departure", "Destruction", "Ejection", "Entrance", "Get", "GetObject2Drop", "Grab", "GrabLost", "Hit", "Hit2", "Hit3", "Incineration", "IncinerationEx", "Initialize", "InitializePlayer", "IsFulfilled", "LiftTop", "LineBreak", "MenuQueryCancel", "OnMenuSelection", "Purchase", "Put", "QueryCatchBlow", "Recruitment", "RejectCollect", "RejectEntrance", "Sale", "Selection", "SellTo", "Stuck", "UpdateTransferZone"  };
	
	public static final String[] KEYWORDS = new String[] {
		Keywords.Break,
		Keywords.Continue,
		Keywords.Else,
		Keywords.For,
		Keywords.If,
		Keywords.Return,
		Keywords.While,
		Keywords.In
	};
	
	public static final String[] DECLARATORS = new String[] {
		Keywords.Const,
		Keywords.Func,
		Keywords.Global,
		Keywords.LocalNamed,
		Keywords.Private,
		Keywords.Protected,
		Keywords.Public,
		Keywords.GlobalNamed,
		Keywords.VarNamed
	};
	
	public static final String[] MAPGENKEYWORDS = new String[] {
		"map",
		"overlay"
	};
	
	public static final String[] DIRECTIVES = C4Directive.arrayOfDirectiveStrings();
	public static final String[] SCRIPTOPERATORS = C4ScriptOperator.arrayOfOperatorNames(); 
}
