package net.arctics.clonk.ui.refactoring;

import net.arctics.clonk.refactoring.RenameDeclarationProcessor;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

class ClonkRenameInputWizardPage extends UserInputWizardPage {

	private RenameDeclarationProcessor processor;
	private Text newNameText;
	
	protected ClonkRenameInputWizardPage(String name, RenameDeclarationProcessor processor) {
		super(name);
		this.processor = processor;
		setTitle(Messages.ClonkRenameInputWizardPage_SupplyTheName);
		setDescription(Messages.ClonkRenameInputWizardPage_SupplyTheNameDesc);
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		Label labelObj = new Label(container, SWT.NULL);
		labelObj.setText(Messages.ClonkRenameInputWizardPage_NewName);
		newNameText = new Text(container, SWT.BORDER | SWT.SINGLE);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		newNameText.setLayoutData(gd);
		newNameText.setText(processor.getNewName());
	}
	
	@Override
	public IWizardPage getNextPage() {
		commitConfigurationToProcessor();
		return super.getNextPage();
	}

	protected void commitConfigurationToProcessor() {
		processor.setNewName(newNameText.getText());
	}
	
	@Override
	protected boolean performFinish() {
		commitConfigurationToProcessor();
		return super.performFinish();
	}

}
