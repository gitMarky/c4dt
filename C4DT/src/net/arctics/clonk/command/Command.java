package net.arctics.clonk.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.parser.ParsingException;
import net.arctics.clonk.parser.SimpleScriptStorage;
import net.arctics.clonk.parser.c4script.C4Function;
import net.arctics.clonk.parser.c4script.C4ScriptBase;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree;
import net.arctics.clonk.parser.c4script.C4ScriptParser;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.ExprElm;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.IExpressionListener;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.Statement;
import net.arctics.clonk.parser.c4script.C4ScriptExprTree.TraversalContinuation;
import net.arctics.clonk.ui.editors.ClonkHyperlink;
import net.arctics.clonk.util.Utilities;

public class Command {
	public static final C4ScriptBase COMMAND_BASESCRIPT;
	
	public static class C4CommandScript extends C4ScriptBase {
		
		private class C4CommandFunction extends C4Function {
            private static final long serialVersionUID = 1L;
            
			private Statement[] statements;
			
			@Override
			public Object invoke(Object... args) {
			    for (Statement s : statements) {
			    	s.evaluate();
			    }
			    // FIXME
			    return null;
			}
		}

        private static final long serialVersionUID = 1L;
        
		private String script;
		private C4CommandFunction main;
		
		@Override
        public ClonkIndex getIndex() {
	        return ClonkCore.getDefault().getExternIndex();
        }

		@Override
		public String getScriptText() {
			return script;
		}
		
		@Override
        public Object getScriptFile() {
			try {
	            return new SimpleScriptStorage(getName(), script);
            } catch (UnsupportedEncodingException e) {
	            e.printStackTrace();
	            return null;
            }
        }
		
		public C4CommandScript(String name, String script) {
			super();
			setName(name);
			this.script = script;
			C4ScriptParser parser = new C4ScriptParser(script, this) {
				@Override
				protected C4Function newFunction() {
				    return new C4CommandFunction();
				}
				@Override
				public void parseCodeOfFunction(C4Function function) throws ParsingException {
				    if (function.getName().equals("Main")) { //$NON-NLS-1$
				    	main = (C4CommandFunction)function;
				    }
				    final List<Statement> statements = new LinkedList<Statement>();
				    this.setExpressionListener(new IExpressionListener() {
						@Override
						public TraversalContinuation expressionDetected(ExprElm expression, C4ScriptParser parser) {
							if (expression instanceof Statement)
								statements.add((Statement)expression);
							return TraversalContinuation.Continue;
						}
					});
				    super.parseCodeOfFunction(function);
				    ((C4CommandFunction)function).statements = statements.toArray(new Statement[statements.size()]);
				    this.setExpressionListener(null);
				}
			};
			try {
	            parser.parse();
            } catch (ParsingException e) {
	            e.printStackTrace();
            }
		}
		
		@Override
		public C4ScriptBase[] getIncludes(ClonkIndex index) {
			return new C4ScriptBase[] {
				COMMAND_BASESCRIPT
			};
		}
		
		public C4CommandFunction getMain() {
			return main;
		}
		
		public Object invoke(Object... args) {
			return main.invoke(args);
		}
		
	}
	
	static {
		COMMAND_BASESCRIPT = new C4ScriptBase() {
            private static final long serialVersionUID = 1L;
            
			@Override
			public ClonkIndex getIndex() {
			    return ClonkCore.getDefault().getExternIndex();
			}
			@Override
			public Object getScriptFile() {
			    try {
	                return new SimpleScriptStorage("CommandBase", ""); //$NON-NLS-1$ //$NON-NLS-2$
                } catch (UnsupportedEncodingException e) {
	                return null;
                }
			}
			
			@Override
			public String getName() {
				return "CommandBaseScript"; //$NON-NLS-1$
			};
			
			@Override
			public String getNodeName() {
				return getName();
			};
			
		};
		
		registerCommandsFromClass(Command.class);
		registerCommandsFromClass(DebugCommands.class);
		registerCommandsFromClass(CodeConversionCommands.class);
		registerCommandsFromClass(EngineConfiguration.class);
	}

	private static void registerCommandsFromClass(Class<?> classs) {
		for (Method m : classs.getMethods()) {
			if (m.getAnnotation(CommandFunction.class) != null)
				addCommand(m);
		}
	}
	
	private static class C4CommandFunction extends C4Function {

        private static final long serialVersionUID = 1L;
        
        private final Method method;
        
        @Override
        public Object invoke(Object... args) {
        	try {
	            return method.invoke(null, Utilities.concat(this, args));
            } catch (Exception e) {
	            e.printStackTrace();
	            return null;
            }
        }
        
        public C4CommandFunction(C4ScriptBase parent, Method method) {
        	super(method.getName(), parent, C4FunctionScope.FUNC_PUBLIC);
        	this.method = method;
        }
		
	}
	
	public static void addCommand(Method method) {
		COMMAND_BASESCRIPT.addDeclaration(new C4CommandFunction(COMMAND_BASESCRIPT, method));
	}
	
	public static void setFieldValue(Object obj, String name, Object value) {
		Class<?> c = obj instanceof Class<?> ? (Class<?>)obj : obj.getClass();
		try {
			Field f = c.getField(name);
			if (value instanceof Long && f.getType() == Integer.TYPE) {
				value = ((Long)value).intValue();
			}
			else if (value instanceof String && f.getType().getSuperclass() == Enum.class) {
				value = f.getType().getMethod("valueOf", String.class).invoke(f.getClass(), value);
			}
			f.set(obj, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@CommandFunction
	public static void Log(Object context, String message) {
		System.out.println(message);
	}
	
	@CommandFunction
	public static String Format(Object context, String format, Object... args) {
		return String.format(format, args);
	}
	
	@CommandFunction
	public static void OpenDoc(Object context, String funcName) {
		try {
			ClonkHyperlink.openDocumentationForFunction(funcName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static class CodeConversionCommands {
		@CommandFunction
		public static void SetCodeConversionOption(Object context, String option, Object value) {
			setFieldValue(C4ScriptExprTree.class, option, value);
		}
	}
	
	public static class DebugCommands {

		private static Socket debugSocket;
		private static PrintWriter debugSocketWriter;
		private static BufferedReader debugSocketReader;

		@CommandFunction
		public static void ConnectToDebugSocket(Object context, long port) {
			try {
				debugSocket = new Socket("localhost", (int) port); //$NON-NLS-1$
				debugSocketWriter = new PrintWriter(debugSocket.getOutputStream());
				debugSocketReader = new BufferedReader(new InputStreamReader(debugSocket.getInputStream()));
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@CommandFunction
		public static void CloseDebugSocket(Object context) {
			if (debugSocket != null)
				try {
					debugSocketReader.close();
					debugSocketReader = null;
					debugSocketWriter.close();
					debugSocketWriter = null;
					debugSocket.close();
					debugSocket = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		@CommandFunction
		public static void SendToDebugSocket(Object context, String command) {
			if (debugSocketWriter != null) {
				debugSocketWriter.println(command);
				debugSocketWriter.flush();
			}
			String line;
			try {
				if ((line = debugSocketReader.readLine()) != null)
					System.out.println(line);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		@CommandFunction
		public static void Testing(Object context, String value) {
			for (int i = 0; i < value.length(); i++)
				System.out.println((int)value.charAt(i));
			System.out.println(Integer.parseInt(value));
		}

	}
	
	public static class EngineConfiguration {
		@CommandFunction
		public static void SetEngineProperty(Object context, String name, Object value) {
			setFieldValue(ClonkCore.getDefault().getActiveEngine(), name, value);
		}
	}
	
}
