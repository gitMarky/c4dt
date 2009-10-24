package net.arctics.clonk.ui.wizards;

import java.lang.reflect.Field;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;

public class NewClonkFolderWizardPage extends WizardPage {

	public interface IAdditionToTextField {
		public void fill(Composite container, Text textField);
	}
	
	protected Text containerText;
	protected Text fileText;
	private String folderExtension;
	protected ISelection selection;
	protected IProject project;
	
	public NewClonkFolderWizardPage(ISelection selection) {
		super("wizardPage"); //$NON-NLS-1$
		setTitle(Messages.NewClonkFolderWizardPage_1);
		setDescription(Messages.NewClonkFolderWizardPage_2);
		this.selection = selection;
	}
	
	public Text addTextField(String label) {
		return addTextField(label, null, null, null);
	}
	
	public Text addTextField(String label, IAdditionToTextField addition) {
		return addTextField(label, null, null, addition);
	}
	
	public Text addTextField(String label, final Object context, final String property, IAdditionToTextField addition) {
		Composite container = (Composite) getControl();
		Label labelObj = new Label(container, SWT.NULL);
		labelObj.setText(label);
		Text result = new Text(container, SWT.BORDER | SWT.SINGLE);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		result.setLayoutData(gd);
		result.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent event) {
				if (context != null && property != null) {
					try {
						Field field = context.getClass().getField(property);
						field.set(context, ((Text)event.widget).getText());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				dialogChanged();
			}
		});
		if (addition != null)
			addition.fill(container, result);
		else {
			// fill last cell with dummy
			new Label(container, SWT.NULL);
		}
		return result;
	}
	
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 3;
		layout.verticalSpacing = 9;
		
		containerText = addTextField(Messages.NewClonkFolderWizardPage_3, new IAdditionToTextField() {
			public void fill(Composite container, Text textField) {
				Button button = new Button(container, SWT.PUSH);
				button.setText(Messages.NewClonkFolderWizardPage_4);
				button.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						handleBrowse();
					}
				});
			}
		});
		fileText = addTextField(Messages.NewClonkFolderWizardPage_5);
		
		if (getClass() == NewClonkFolderWizardPage.class) {
			initialize();
			dialogChanged();
		}
	}
	
	/**
	 * Ensures that both text fields are set.
	 */
	
	protected void dialogChanged() {
		IResource container = ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
		String fileName = getFileName();

		if (getContainerName().length() == 0) {
			updateStatus(Messages.NewClonkFolderWizardPage_6);
			return;
		}
		if (container == null
				|| (container.getType() & (IResource.PROJECT | IResource.FOLDER)) == 0) {
			updateStatus(Messages.NewClonkFolderWizardPage_7);
			return;
		}
		if (!container.isAccessible()) {
			updateStatus(Messages.NewClonkFolderWizardPage_8);
			return;
		}
		if (fileName.length() == 0) {
			updateStatus(Messages.NewClonkFolderWizardPage_9);
			return;
		}
		if (fileName.replace('\\', '/').indexOf('/', 1) > 0) {
			updateStatus(Messages.NewClonkFolderWizardPage_10);
			return;
		}
		updateStatus(null);
	}
	
	protected void updateStatus(String message) {
		setErrorMessage(message);
		setPageComplete(message == null);
	}
	
	/**
	 * Uses the standard container selection dialog to choose the new value for
	 * the container field.
	 */

	protected void handleBrowse() {
		ContainerSelectionDialog dialog = new ContainerSelectionDialog(
				getShell(), ResourcesPlugin.getWorkspace().getRoot(), false,
				Messages.NewClonkFolderWizardPage_11);
		if (dialog.open() == ContainerSelectionDialog.OK) {
			Object[] result = dialog.getResult();
			if (result.length == 1) {
				containerText.setText(((Path) result[0]).toString());
			}
		}
	}
	
	/**
	 * Tests if the current workbench selection is a suitable container to use.
	 */

	protected void initialize() {
		if (selection != null && selection.isEmpty() == false && selection instanceof IStructuredSelection) {
			IStructuredSelection ssel = (IStructuredSelection) selection;
			if (ssel.size() > 1)
				return;
			Object obj = ssel.getFirstElement();
			if (obj instanceof IResource) {
				IContainer container;
				if (obj instanceof IContainer)
					container = (IContainer) obj;
				else
					container = ((IResource) obj).getParent();
				containerText.setText(container.getFullPath().toString());
				project = ((IResource)obj).getProject();
			}
		}
		fileText.setText(Messages.NewClonkFolderWizardPage_0);
	}
	
	public String getContainerName() {
		return containerText.getText();
	}
	
	public String getFileName() {
		return fileText.getText() + getFolderExtension();
	}
	
	public String getFolderExtension() {
		return folderExtension;
	}
	
	public void setFolderExtension(String value) {
		folderExtension = value;
	}

	public Text getContainerText() {
		return containerText;
	}

	public Text getFileText() {
		return fileText;
	}

}
