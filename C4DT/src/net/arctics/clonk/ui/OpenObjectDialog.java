package net.arctics.clonk.ui;

import java.util.Comparator;
import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.ClonkIndex;
import net.arctics.clonk.resource.ClonkProjectNature;
import net.arctics.clonk.util.ArrayUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;

public class OpenObjectDialog extends FilteredItemsSelectionDialog {
	
	public static final String DIALOG_SETTINGS = "OpenObjectDialogSettings"; //$NON-NLS-1$
	
	private static class OpenObjectLabelProvider extends LabelProvider implements IStyledLabelProvider {

		public StyledString getStyledText(Object element) {
			if (element == null)
				return new StyledString(Messages.OpenObjectDialog_Empty);
			if (!(element instanceof Definition)) return new StyledString(element.toString());
			Definition obj = (Definition) element;
			StyledString buf = new StyledString(obj.getName());
			buf.append(" - ", StyledString.QUALIFIER_STYLER); //$NON-NLS-1$
			buf.append(obj.getId().getName(), StyledString.QUALIFIER_STYLER);
			return buf;
		}
		
	}
	
	public OpenObjectDialog(Shell shell) {
		super(shell, true);
		setListLabelProvider(new OpenObjectLabelProvider());
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		// There's nothing special here
		return null;
	}

	@Override
	protected ItemsFilter createFilter() {
		return new ItemsFilter() {
		
			@Override
			public boolean matchItem(Object item) {
				if (!(item instanceof Definition)) return false;
				final Definition obj = (Definition) item;
				final String search = this.getPattern().toUpperCase();
				if (obj == null || obj.getId() == null || obj.getName() == null || search == null) {
					return false;
				}
				return obj.nameContains(search);
			}
		
			@Override
			public boolean isConsistentItem(Object item) {
				// TODO Auto-generated method stub ??
				return false;
			}
		}; 
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider,
			ItemsFilter itemsFilter, IProgressMonitor progressMonitor)
			throws CoreException {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for(IProject project : projects) {
			if (project.isOpen()) {
				if (project.isNatureEnabled(ClonkCore.CLONK_NATURE_ID)) {
					ClonkProjectNature nature = ClonkProjectNature.get(project);
					ClonkIndex index = nature.getIndex();
					fillWithIndexContents(contentProvider, itemsFilter,
							progressMonitor, index);
				}
			}
		}
	}

	private void fillWithIndexContents(AbstractContentProvider contentProvider,
			ItemsFilter itemsFilter, IProgressMonitor progressMonitor,
			ClonkIndex index) {
		progressMonitor.beginTask(Messages.OpenObjectDialog_Searching, index.numUniqueIds());
		for(Definition object : index) {
			contentProvider.add(object, itemsFilter);
			progressMonitor.worked(1);
		}
		progressMonitor.done();
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = ClonkCore.getDefault().getDialogSettings()
				.getSection(DIALOG_SETTINGS);
		if (settings == null) {
			settings = ClonkCore.getDefault().getDialogSettings()
					.addNewSection(DIALOG_SETTINGS);
		}
		return settings;
	}

	@Override
	public String getElementName(Object item) {
		if (!(item instanceof Definition))
			return item.toString();
		return ((Definition)item).getId().getName();
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected Comparator getItemsComparator() {
		return new Comparator() {
			public int compare(Object arg0, Object arg1) {
				return arg0.toString().compareTo(arg1.toString());
			}
		};
	}

	@Override
	protected IStatus validateItem(Object item) {
		return Status.OK_STATUS;
	}
	
	public Definition[] getSelectedObjects() {
		return ArrayUtil.convertArray(getResult(), Definition.class);
	}

}
