package net.arctics.clonk.debug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.arctics.clonk.debug.ClonkDebugTarget.Commands;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.index.ProjectIndex;
import net.arctics.clonk.parser.c4script.Function;
import net.arctics.clonk.parser.c4script.ScriptBase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;

public class ClonkDebugThread extends ClonkDebugElement implements IThread {
	
	private static final ClonkDebugStackFrame[] NO_STACKFRAMES = new ClonkDebugStackFrame[0];
	
	private ClonkDebugStackFrame[] stackFrames;
	
	private Map<ScriptBase, Function[]> lineToFunctionMaps = new HashMap<ScriptBase, Function[]>(); 
	
	private void nullOut() {
		stackFrames = NO_STACKFRAMES;
	}
	
	public ScriptBase findScript(String path, ClonkIndex index, Set<ClonkIndex> alreadySearched) throws CoreException {
		if (alreadySearched.contains(index))
			return null;
		ScriptBase script = index.findScriptByPath(path);
		if (script != null)
			return script;
		alreadySearched.add(index);
		if (index instanceof ProjectIndex) {
			for (IProject proj : ((ProjectIndex) index).getProject().getReferencedProjects()) {
				ProjectIndex projIndex = ProjectIndex.get(proj);
				if (projIndex != null) {
					ScriptBase _result = findScript(path, projIndex, alreadySearched);
					if (_result != null)
						return _result;
				}
			}
		}
		return null;
	}

	public void setStackTrace(List<String> stackTrace) throws CoreException {
		ProjectIndex index = ProjectIndex.get(getTarget().getScenario().getProject());
		if (index == null) {
			nullOut();
			return;
		}
		ClonkDebugStackFrame[] newStackFrames = new ClonkDebugStackFrame[stackTrace.size()];
		int stillToBeReused = stackFrames != null ? stackFrames.length : 0;
		for (int i = 0; i < stackTrace.size(); i++) {
			String sourcePath = stackTrace.get(i);
			
			if (sourcePath == null) {
				nullOut();
				return;
			}
			String fullSourcePath = sourcePath;
			int delim = sourcePath.lastIndexOf(':');
			String linePart = sourcePath.substring(delim+1);
			int line = Integer.parseInt(linePart);
			sourcePath = sourcePath.substring(0, delim);
			ScriptBase script = findScript(sourcePath, index, new HashSet<ClonkIndex>());
			Function f = script != null ? funcAtLine(script, line) : null;
			Object funObj = f != null ? f : fullSourcePath;
			if (stillToBeReused > 0) {
				if (stackFrames[stillToBeReused-1].getFunction().equals(funObj)) {
					newStackFrames[i] = stackFrames[--stillToBeReused];
					newStackFrames[i].setLine(line);
					continue;
				}
			}
			newStackFrames[i] = new ClonkDebugStackFrame(this, f != null ? f : fullSourcePath, line);
		}
		stackFrames = newStackFrames;
	}
	
	private Function funcAtLine(ScriptBase script, int line) {
		line--;
		Function[] map = lineToFunctionMaps.get(script);
		if (map == null) {
			map = script.calculateLineToFunctionMap();
			lineToFunctionMaps.put(script, map);
		}
		return line >= 0 && line < map.length ? map[line] : null;
	}

	public ClonkDebugThread(ClonkDebugTarget target) {
		super(target);
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	@Override
	public IBreakpoint[] getBreakpoints() {
		return new IBreakpoint[0];
	}

	@Override
	public String getName() throws DebugException {
		return Messages.MainThread;
	}

	@Override
	public int getPriority() throws DebugException {
		return 1;
	}

	@Override
	public IStackFrame[] getStackFrames() throws DebugException {
		return hasStackFrames() ? stackFrames : NO_STACKFRAMES;
	}

	@Override
	public IStackFrame getTopStackFrame() throws DebugException {
		return hasStackFrames() ? stackFrames[0] : null;
	}

	@Override
	public boolean hasStackFrames() throws DebugException {
		return stackFrames != null && stackFrames.length > 0 && isSuspended();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public boolean canResume() {
		return getTarget().canResume();
	}

	@Override
	public boolean canSuspend() {
		return getTarget().canSuspend();
	}

	@Override
	public boolean isSuspended() {
		return getTarget().isSuspended();
	}

	@Override
	public void resume() throws DebugException {
		getTarget().resume();
	}

	@Override
	public void suspend() throws DebugException {
		getTarget().suspend();
		//fireSuspendEvent(DebugEvent.CLIENT_REQUEST);
	}

	@Override
	public boolean canStepInto() {
		return isSuspended();
	}

	@Override
	public boolean canStepOver() {
		return isSuspended();
	}

	@Override
	public boolean canStepReturn() {
		return isSuspended();
	}

	@Override
	public boolean isStepping() {
		return getTarget().isSuspended();
	}

	@Override
	public void stepInto() throws DebugException {
		getTarget().send(Commands.SUSPEND);
	}

	@Override
	public void stepOver() throws DebugException {
		getTarget().send(Commands.STEPOVER);
	}

	@Override
	public void stepReturn() throws DebugException {
		getTarget().send(Commands.STEPRETURN);
	}

	@Override
	public boolean canTerminate() {
		return getTarget().canTerminate();
	}

	@Override
	public boolean isTerminated() {
		return getTarget().isTerminated();
	}

	@Override
	public void terminate() throws DebugException {
		getTarget().terminate();
		fireTerminateEvent();
	}

}
