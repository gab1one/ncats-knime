package gov.nih.ncats.knime.lychi;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "Lychi Resolver" Node. This node uses the
 * LyChI library (https://github.com/ncats/lychi) developed by the Informatics
 * Group at the NCATS/NIH to convert SMILES into LyChI identifier.
 *
 * @author Vishal Siramshetty (siramshettyv2@nih.gov)
 */
public class LychiResolverNodeFactory extends NodeFactory<LychiResolverNodeModel> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LychiResolverNodeModel createNodeModel() {
		return new LychiResolverNodeModel();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNrNodeViews() {
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeView<LychiResolverNodeModel> createNodeView(final int viewIndex,
			final LychiResolverNodeModel nodeModel) {
		return new LychiResolverNodeView(nodeModel);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasDialog() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeDialogPane createNodeDialogPane() {
		return new LychiResolverNodeDialog();
	}

}
