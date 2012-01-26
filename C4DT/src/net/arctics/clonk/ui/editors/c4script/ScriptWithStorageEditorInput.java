package net.arctics.clonk.ui.editors.c4script;

import java.lang.ref.WeakReference;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.util.ITreeNode;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;

public class ScriptWithStorageEditorInput extends PlatformObject implements IEditorInput, IPathEditorInput, IStorageEditorInput, IPersistableElement {

	private static final String FACTORY_ID = ClonkCore.id("ui.editors.scriptWithStorageEditorInputFactory");   //$NON-NLS-1$
	
	private WeakReference<Script> script;
	
	public ScriptWithStorageEditorInput(Script script) {
		super();
		
		if (!(script.scriptStorage() instanceof IStorage))
			throw new IllegalArgumentException("script"); //$NON-NLS-1$
		this.script = new WeakReference<Script>(script);
	}

	@Override
	public boolean exists() {
		return script != null && script.get() != null;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return ClonkCore.instance().getIconImageDescriptor("C4Object"); //$NON-NLS-1$
	}

	@Override
	public String getName() {
		return "[" + getScript().name() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public IPersistableElement getPersistable() {
		return this;
	}

	@Override
	public String getToolTipText() {
		return ((ITreeNode)getScript()).path().toOSString();
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class cls) {
		return null;
	}

	@Override
	public IPath getPath() {
		try {
			if (script instanceof ITreeNode)
				return ((ITreeNode)script).path();
			return getStorage().getFullPath();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		return (obj instanceof ScriptWithStorageEditorInput && ((ScriptWithStorageEditorInput)obj).getScript() == getScript());
	}

	@Override
	public IStorage getStorage() throws CoreException {
		return getScript().scriptStorage();
	}

	public Script getScript() {
		return script.get();
	}

	@Override
	public String getFactoryId() {
		return FACTORY_ID;
	}

	@Override
	public void saveState(IMemento memento) {
		memento.putString("path", getPath().toPortableString()); //$NON-NLS-1$
	}

}
