package gov.nih.ncats.knime.lychi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.knime.chem.types.SdfValue;
import org.knime.chem.types.SmilesValue;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataValue;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * This is the model implementation of Lychi Resolver. This node uses the LyChI
 * library (https://github.com/ncats/lychi) developed by the Informatics Group
 * at the NCATS/NIH to convert SMILES into LyChI identifier.
 *
 * @author Vishal Siramshetty (siramshettyv2@nih.gov)
 */
public class LychiResolverNodeModel extends NodeModel {

	/**
	 * The logger is used to print info/warning/error messages to the KNIME console
	 * and to the KNIME log file. Retrieve it via 'NodeLogger.getLogger' providing
	 * the class of this node model.
	 */
	private static final NodeLogger logger = NodeLogger.getLogger(LychiResolverNodeModel.class);

	/** Input data info index for Smiles value. */
	protected static final int INPUT_COLUMN_SMI = 0;

	/** Settings model for the column name of the input column. */
	private final SettingsModelString m_modelInputColumnName = LychiResolverNodeDialog.createInputColumnNameModel();

	/**
	 * Settings model for the column name of the new column to be added to the
	 * output table.
	 */
	private final SettingsModelString m_modelNewColumnName = LychiResolverNodeDialog.createNewColumnNameModel();

	/** Settings model for the option to keep or remove salt. */
	private final SettingsModelBoolean m_modelSaltSolvent = LychiResolverNodeDialog.createSaltSolventOptionModel();

	/** Settings model for the option to turn on keto-enol tautomerism. */
	private final SettingsModelBoolean m_modelKetoEnol = LychiResolverNodeDialog.createKetoEnolOptionModel();

	/**
	 * Constructor for the node model.
	 */
	protected LychiResolverNodeModel() {

		// one incoming port and two outgoing ports
		super(1, 2);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {

		int maxParallelWorkers = (int) Math.ceil(1.5 * Runtime.getRuntime().availableProcessors());
		int maxQueueSize = 10 * maxParallelWorkers;

		// get boolean values from input table
		boolean removeSaltSolvent = m_modelSaltSolvent.getBooleanValue();
		boolean ketoEnol = m_modelKetoEnol.getBooleanValue();

		// check input table spec
		BufferedDataTable inputTable = inData[0];
		DataTableSpec inputTableSpec = inputTable.getDataTableSpec();
		int smi_index = inputTableSpec.findColumnIndex(m_modelInputColumnName.getStringValue());

		if (inputTableSpec.getColumnSpec(smi_index).getType().isCompatible(SmilesValue.class)
				|| inputTableSpec.getColumnSpec(smi_index).getType().isCompatible(SdfValue.class)) {
			logger.info("## Input type acceptable");
		} else {
			logger.info("## Input type not acceptable");
		}

		// configure output tables
		DataTableSpec outputTableSpec = createOutputSpec(inputTableSpec);
		DataTableSpec errorTableSpec = createErrorTableSpec(inputTableSpec);
		BufferedDataContainer outputContainer = exec.createDataContainer(outputTableSpec);
		BufferedDataContainer errorContainer = exec.createDataContainer(errorTableSpec);

		// multi-thread execution
		LychiResolverNodeWorker worker = new LychiResolverNodeWorker(maxQueueSize, maxParallelWorkers, smi_index, exec,
				inData[0].size(), outputContainer, errorContainer, removeSaltSolvent, ketoEnol);

		try {
			worker.run(inData[0]);
		} finally {
			outputContainer.close();
			errorContainer.close();
		}

		// once input table is processed, close the containers and return the tables
		outputContainer.close();
		errorContainer.close();
		
		BufferedDataTable out = outputContainer.getTable();
		BufferedDataTable err = errorContainer.getTable();
		
		@SuppressWarnings("deprecation")
		int err_rows = err.getRowCount();
		if(err_rows > 0) {
			setWarningMessage("Failed to process " + err_rows + " rows. Check the second output port for more details.");
		}
		
		return new BufferedDataTable[] { out, err };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		// list of acceptable input column types
		final List<Class<? extends DataValue>> listValueClasses = new ArrayList<Class<? extends DataValue>>();
		listValueClasses.add(SmilesValue.class);
		listValueClasses.add(SdfValue.class);

		// Auto guess the input column if not set - fails if no compatible column found
		autoGuessColumn(inSpecs[0], m_modelInputColumnName, listValueClasses, 0,
				"No SMILES or MOL/SDF compatible column in input table.");

		// Determines, if the input column exists - fails if it does not
		checkColumnExistence(inSpecs[0], m_modelInputColumnName, listValueClasses);

		// Determine, if the new column name has been set and if it is really unique
		checkColumnNameUniqueness(inSpecs[0], null, m_modelNewColumnName, "Output column has not been specified yet.",
				"The name %COLUMN_NAME% of the new column exists already in the input.");

		DataTableSpec inputTableSpec = inSpecs[0];

		return new DataTableSpec[] { createOutputSpec(inputTableSpec), createErrorTableSpec(inputTableSpec) };
	}

	/**
	 * Given an input table spec, check if the output column name already exists is
	 * present in the input table. Original source: RDKit KNIME
	 * (https://github.com/rdkit/knime-rdkit/blob/master/org.rdkit.knime.nodes/src/org/rdkit/knime/util/SettingsUtils.java)
	 * 
	 * @param inSpec
	 * @param arrMoreColumnNames
	 * @param settingNewColumnName
	 * @param strErrorIfNotSet
	 * @param strErrorIfNotUnique
	 * @return
	 * @throws InvalidSettingsException
	 */
	public static boolean checkColumnNameUniqueness(final DataTableSpec inSpec, final String[] arrMoreColumnNames,
			final SettingsModelString settingNewColumnName, final String strErrorIfNotSet,
			final String strErrorIfNotUnique) throws InvalidSettingsException {

		if (settingNewColumnName == null) {
			throw new IllegalArgumentException("Settings model for new column name must not be null.");
		}

		return checkColumnNameUniqueness(inSpec, settingNewColumnName.getStringValue(), strErrorIfNotSet,
				strErrorIfNotUnique);
	}

	/**
	 * Given an input table spec, check if the output column name already exists is
	 * present in the input table. Original source: RDKit KNIME
	 * (https://github.com/rdkit/knime-rdkit/blob/master/org.rdkit.knime.nodes/src/org/rdkit/knime/util/SettingsUtils.java)
	 * 
	 * @param inSpec
	 * @param strNewColumnName
	 * @param strErrorIfNotSet
	 * @param strErrorIfNotUnique
	 * @return
	 * @throws InvalidSettingsException
	 */
	private static boolean checkColumnNameUniqueness(DataTableSpec inSpec, String strNewColumnName,
			String strErrorIfNotSet, String strErrorIfNotUnique) throws InvalidSettingsException {

		boolean bRet = true;
		String strColumnNameToCheck = (strNewColumnName == null ? null : strNewColumnName.trim());

		// pre-checks
		if (inSpec == null) {
			throw new IllegalArgumentException("Input table spec must not be null.");
		}

		// Check, if we have no setting yet
		if (strColumnNameToCheck == null || strColumnNameToCheck.isEmpty()) {
			if (strErrorIfNotSet != null) {
				throw new InvalidSettingsException(strErrorIfNotSet);
			}
		}

		// Check, if the column name is not existing yet (means, it is unique)
		else {
			final List<String> listColumnNames = Arrays.asList(inSpec.getColumnNames());

			// Column is not unique - throw an exception if requested
			if (listColumnNames.contains(strColumnNameToCheck)) {
				bRet = false;
				if (strErrorIfNotUnique != null) {
					throw new InvalidSettingsException(strErrorIfNotUnique.replace("%COLUMN_NAME%",
							strColumnNameToCheck == null ? "null" : strColumnNameToCheck));
				}
			}
		}

		return bRet;
	}

	/**
	 * From a given input table spec, automatically guess a column name based on its
	 * data value type. Original source: RDKit KNIME
	 * (https://github.com/rdkit/knime-rdkit/blob/master/org.rdkit.knime.nodes/src/org/rdkit/knime/util/SettingsUtils.java)
	 * 
	 * @param inSpec
	 * @param settingColumn
	 * @param listValueClasses
	 * @param indexOfFindingsToBeUsed
	 * @param strErrorIfNotFound
	 * @return
	 * @throws InvalidSettingsException
	 */
	public boolean autoGuessColumn(final DataTableSpec inSpec, final SettingsModelString settingColumn,
			final List<Class<? extends DataValue>> listValueClasses, final int indexOfFindingsToBeUsed,
			final String strErrorIfNotFound) throws InvalidSettingsException {

		boolean bRet = false;

		// pre-checks
		if (inSpec == null) {
			throw new IllegalArgumentException("Input table spec must not be null.");
		}
		if (settingColumn == null) {
			throw new IllegalArgumentException("Settings model for column guessing must not be null.");
		}
		if (listValueClasses == null) {
			throw new IllegalArgumentException("Value class list must not be null.");
		}
		if (listValueClasses.contains(null)) {
			throw new IllegalArgumentException("Value class list must not contain null elements.");
		}

		// Auto guessing the input column name, if it was not set yet
		if (settingColumn instanceof SettingsModelColumnName && ((SettingsModelColumnName) settingColumn).useRowID()) {
			bRet = true;

		} else if (settingColumn.getStringValue() == null) {

			final LinkedHashSet<String> compatibleCols = new LinkedHashSet<String>();

			// find all compatible columns in the input table
			for (final Class<? extends DataValue> valueClass : listValueClasses) {
				for (final DataColumnSpec colSpec : inSpec) {
					if (colSpec.getType().isCompatible(valueClass) || colSpec.getType().isAdaptable(valueClass)) {
						compatibleCols.add(colSpec.getName());
						break;
					}
				}
			}

			final String[] arrCompatibleCols = compatibleCols.toArray(new String[compatibleCols.size()]);

			// Use a single column, if only one is compatible, without a warning
			if (indexOfFindingsToBeUsed == arrCompatibleCols.length - 1) {
				settingColumn.setStringValue(arrCompatibleCols[indexOfFindingsToBeUsed]);
				setWarningMessage(
						"Auto selection: Using column \"" + arrCompatibleCols[indexOfFindingsToBeUsed] + "\"");
				bRet = true;
			}

			// Auto-guessing: Use the first matching column
			else if (indexOfFindingsToBeUsed < arrCompatibleCols.length - 1) {

				settingColumn.setStringValue(arrCompatibleCols[indexOfFindingsToBeUsed]);
				setWarningMessage(
						"Auto selection: Using column \"" + arrCompatibleCols[indexOfFindingsToBeUsed] + "\"");
				bRet = true;

			} else if (strErrorIfNotFound != null) {
				throw new InvalidSettingsException(strErrorIfNotFound);
			}

		} else {
			bRet = true;
		}

		return bRet;
	}

	/**
	 * Check if the input column name from the existing settings model is present in
	 * the given input table spec. Original source: RDKit KNIME
	 * (https://github.com/rdkit/knime-rdkit/blob/master/org.rdkit.knime.nodes/src/org/rdkit/knime/util/SettingsUtils.java)
	 * 
	 * @param inSpec
	 * @param settingColumn
	 * @param listValueClasses
	 * @return
	 * @throws InvalidSettingsException
	 */
	public boolean checkColumnExistence(final DataTableSpec inSpec, final SettingsModelString settingColumn,
			final List<Class<? extends DataValue>> listValueClasses) throws InvalidSettingsException {

		boolean bRet = false;

		// Pre-checks
		if (inSpec == null) {
			throw new IllegalArgumentException("Input table spec must not be null.");
		}
		if (settingColumn == null) {
			throw new IllegalArgumentException("Settings model for column name must not be null.");
		}

		// Consider that the user selected to use the row id as column
		if (settingColumn instanceof SettingsModelColumnName && ((SettingsModelColumnName) settingColumn).useRowID()) {
			bRet = true;
		}

		// Process other column names
		else {
			final String strColumnName = settingColumn.getStringValue();

			// Check, if we have no setting yet
			if (strColumnName == null) {
				throw new InvalidSettingsException("Input column has not been specified yet.");
			}

			// Check, if the column name exists
			else if (inSpec.containsName(strColumnName)) {
				// Perform an additional type check, if requested
				if (listValueClasses != null && !listValueClasses.isEmpty()) {
					for (final Class<? extends DataValue> valueClass : listValueClasses) {
						if (inSpec.getColumnSpec(strColumnName).getType().isCompatible(valueClass)
								|| inSpec.getColumnSpec(strColumnName).getType().isAdaptable(valueClass)) {
							bRet = true;
							break;
						}
					}
				}

				else {
					bRet = true;
				}
			}

			// Not found
			if (!bRet) {
				throw new InvalidSettingsException("Input column %COLUMN_NAME% does not exist. Has the table changed?"
						.replace("%COLUMN_NAME%", strColumnName == null ? "null" : strColumnName));
			}
		}

		return bRet;
	}

	/**
	 * Creates the output table spec from the input spec. For each double column in
	 * the input, one String column will be created containing the formatted double
	 * value as String.
	 * 
	 * @param inputTableSpec
	 * @return
	 */
	private DataTableSpec createOutputSpec(DataTableSpec inputTableSpec) {

		DataColumnSpec[] outputColumnSpecs = new DataColumnSpec[inputTableSpec.getNumColumns() + 1];

		for (int i = 0; i < inputTableSpec.getNumColumns(); i++) {
			DataColumnSpec columnSpec = inputTableSpec.getColumnSpec(i);
			outputColumnSpecs[i] = columnSpec;
		}

		outputColumnSpecs[inputTableSpec.getNumColumns()] = new DataColumnSpecCreator(
				DataTableSpec.getUniqueColumnName(inputTableSpec, m_modelNewColumnName.getStringValue()),
				StringCell.TYPE).createSpec();

		return new DataTableSpec(outputColumnSpecs);

	}

	/**
	 * Creates the output table spec for the unpaersed structures based on the input
	 * spec.
	 * 
	 * @param inputTableSpec
	 * @return
	 */
	private DataTableSpec createErrorTableSpec(DataTableSpec inputTableSpec) {

		DataColumnSpec[] outputColumnSpecs = new DataColumnSpec[inputTableSpec.getNumColumns() + 1];

		for (int i = 0; i < inputTableSpec.getNumColumns(); i++) {
			DataColumnSpec columnSpec = inputTableSpec.getColumnSpec(i);
			outputColumnSpecs[i] = columnSpec;
		}

		outputColumnSpecs[inputTableSpec.getNumColumns()] = new DataColumnSpecCreator(
				DataTableSpec.getUniqueColumnName(inputTableSpec, "Error Message"), StringCell.TYPE).createSpec();

		return new DataTableSpec(outputColumnSpecs);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {

		// save user settings

		m_modelInputColumnName.saveSettingsTo(settings);
		m_modelNewColumnName.saveSettingsTo(settings);
		m_modelSaltSolvent.saveSettingsTo(settings);
		m_modelKetoEnol.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {

		// load (valid) settings

		m_modelInputColumnName.loadSettingsFrom(settings);
		m_modelNewColumnName.loadSettingsFrom(settings);
		m_modelSaltSolvent.loadSettingsFrom(settings);
		m_modelKetoEnol.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

		// check if the settings could be applied

		m_modelInputColumnName.validateSettings(settings);
		m_modelNewColumnName.validateSettings(settings);
		m_modelSaltSolvent.validateSettings(settings);
		m_modelKetoEnol.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

		// TODO load internal data.
		// Everything handed to output ports is loaded automatically (data
		// returned by the execute method, models loaded in loadModelContent,
		// and user settings set through loadSettingsFrom - is all taken care
		// of). Load here only the other internals that need to be restored
		// (e.g. data used by the views). But views is disables for now.

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

		// TODO save internal models.
		// Everything written to output ports is saved automatically (data
		// returned by the execute method, models saved in the saveModelContent,
		// and user settings saved through saveSettingsTo - is all taken care
		// of). Save here only the other internals that need to be preserved
		// (e.g. data used by the views). But views is disables for now.

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
		// TODO Code executed on reset.
		// Models build during execute are cleared here.
		// Also data handled in load/saveInternals will be erased here.
	}

}
