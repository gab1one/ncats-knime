package gov.nih.ncats.knime.lychi;

import org.knime.chem.types.SdfValue;
import org.knime.chem.types.SmilesValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "Lychi Resolver" Node. This node uses the
 * LyChI library (https://github.com/ncats/lychi) developed by the Informatics
 * Group at the NCATS/NIH to convert SMILES into LyChI identifier.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more
 * complex dialog please derive directly from
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Vishal Siramshetty (siramshettyv2@nih.gov)
 */
public class LychiResolverNodeDialog extends DefaultNodeSettingsPane {

	/**
	 * New pane for configuring LychiResolver node dialog.
	 */
	@SuppressWarnings("unchecked")
	protected LychiResolverNodeDialog() {
		super();

		super.addDialogComponent(new DialogComponentColumnNameSelection(createInputColumnNameModel(),
				"SMILES/SDF column: ", LychiResolverNodeModel.INPUT_COLUMN_SMI, SmilesValue.class, SdfValue.class));

		super.addDialogComponent(new DialogComponentString(createNewColumnNameModel(), "Output column name: "));

		super.addDialogComponent(new DialogComponentBoolean(createSaltSolventOptionModel(), "remove salt/solvent"));

		super.addDialogComponent(new DialogComponentBoolean(createKetoEnolOptionModel(), "keto-enol tautomerism"));

	}

	//
	// Static Methods
	//

	/**
	 * Creates the settings model to be used for the input column.
	 * 
	 * @return Settings model for input column selection.
	 */
	static final SettingsModelString createInputColumnNameModel() {
		return new SettingsModelString("input_column", null);
	}

	/**
	 * Creates the settings model to be used to specify the new column name.
	 * 
	 * @return Settings model for new column name.
	 */
	static final SettingsModelString createNewColumnNameModel() {
		return new SettingsModelString("new_column_name", "Lychi");
	}

	/**
	 * Creates the settings model for the boolean flag to determine, if the source
	 * column shall be removed from the result table. The default is false.
	 * 
	 * @return Settings model for check box whether to keep or remove
	 *         salts/solvents.
	 */
	static final SettingsModelBoolean createSaltSolventOptionModel() {
		return new SettingsModelBoolean("remove_salt_solvent", true);
	}

	/**
	 * Creates the settings model for the boolean flag to determine, if the source
	 * column shall be removed from the result table. The default is false.
	 * 
	 * @return Settings model for check box whether to turn on keto-enol
	 *         tautomerism.
	 */
	public static SettingsModelBoolean createKetoEnolOptionModel() {
		return new SettingsModelBoolean("keto_enol_tautomerism", false);
	}

}
