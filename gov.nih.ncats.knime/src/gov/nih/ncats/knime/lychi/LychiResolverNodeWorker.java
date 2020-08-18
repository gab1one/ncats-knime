package gov.nih.ncats.knime.lychi;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.knime.base.data.append.column.AppendedColumnRow;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataType;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.util.MultiThreadWorker;

import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import lychi.LyChIStandardizer;
import lychi.TautomerGenerator;
import lychi.tautomers.SayleDelanyTautomerGenerator;

/**
 * This is the worker node for Lychi Resolver. Original Source: CDK KNIME
 * (https://github.com/cdk/nodes4knime/blob/master/org.openscience.cdk.knime/src/org/openscience/cdk/knime/convert/molecule2cdk/Molecule2CDKWorker.java)
 *
 * @author Vishal Siramshetty (siramshettyv2@nih.gov)
 */

@SuppressWarnings("deprecation")
public class LychiResolverNodeWorker extends MultiThreadWorker<DataRow, DataRow> {

	private final ExecutionContext exec;
	private final BufferedDataContainer bdc1;
	private final BufferedDataContainer bdc2;
	private final double max;
	private final int columnIndex;
	private final boolean removeSaltSolvent;
	private final boolean ketoEnol;

	public LychiResolverNodeWorker(final int maxQueueSize, final int maxActiveInstanceSize, final int columnIndex,
			final ExecutionContext exec, final long max, final BufferedDataContainer bdc1, final BufferedDataContainer bdc2, boolean removeSaltSolvent,
			boolean ketoEnol) {
		super(maxQueueSize, maxActiveInstanceSize);
		this.exec = exec;
		this.bdc1 = bdc1;
		this.bdc2 = bdc2;
		this.max = max;
		this.columnIndex = columnIndex;
		this.removeSaltSolvent = removeSaltSolvent;
		this.ketoEnol = ketoEnol;

	}

	@Override
	protected DataRow compute(DataRow row, long index) throws Exception {
		// TODO Auto-generated method stub

		DataCell cell = row.getCell(columnIndex);
		
		if (!cell.isMissing()) {
			// IAtomContainer mol = converter.convert(getNotation(cell));
			Molecule mol = parseSMILES(cell.toString());
			String lychi_hk = mol.getProperty("LyChI_HK").trim();
			if (lychi_hk != null && !lychi_hk.isEmpty()) {
				cell = new StringCell(lychi_hk);
			} else {
				cell = DataType.getMissingCell();
			}
		}

		row = new AppendedColumnRow(row, cell);

		return row;
	}

	/**
	 * Parse the given SMILES string and return a standardized molecule object.
	 * 
	 * @param smiles
	 * @param std
	 * @return
	 * @throws Exception
	 */
	private Molecule parseSMILES(String smiles) throws Exception {

		TautomerGenerator tg =
				// new NCGCTautomerGenerator ()
				new SayleDelanyTautomerGenerator(1001);

		if (ketoEnol) {
			// hanlding keto-enol... might be too slow
			((SayleDelanyTautomerGenerator) tg).set(SayleDelanyTautomerGenerator.FLAG_ALL);
			// logger.info("## Keto-Enol tautomerism is on");
		} else {
			// logger.info("## Keto-Enol tautomerism is off");
		}

		LyChIStandardizer std = new LyChIStandardizer(tg);
		std.removeSaltOrSolvent(removeSaltSolvent);

		MolHandler mh = new MolHandler();
		mh.setMolecule(smiles);
		Molecule mol = mh.getMolecule();

		std.standardize(mol);

		//String std_smi = ChemUtil.canonicalSMILES(mol, true);
		String hk = LyChIStandardizer.hashKey(mol);

		//mol.setProperty("LyChI_SMILES", std_smi);
		mol.setProperty("LyChI_HK", hk);

		return mol;
	}

	@Override
	protected void processFinished(ComputationTask task)
			throws ExecutionException, CancellationException, InterruptedException {

		try {
			DataRow append = task.get();
			if (!append.getCell(columnIndex).isMissing()) {
				bdc1.addRowToTable(append);
			}
		}catch(Exception ex) {
			DataCell cell = new StringCell(ex.getLocalizedMessage());
			DataRow row = new AppendedColumnRow(task.getInput(), cell);
			bdc2.addRowToTable(row);
		}

		exec.setProgress(this.getFinishedCount() / max, this.getFinishedCount() + " (active/submitted: "
				+ this.getActiveCount() + "/" + (this.getSubmittedCount() - this.getFinishedCount()) + ")");

		try {
			exec.checkCanceled();
		} catch (CanceledExecutionException cee) {
			throw new CancellationException();
		}

	}

}
