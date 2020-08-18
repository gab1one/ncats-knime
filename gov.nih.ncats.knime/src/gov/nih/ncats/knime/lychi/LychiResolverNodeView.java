package gov.nih.ncats.knime.lychi;

import org.knime.core.node.NodeView;

/**
 * <code>NodeView</code> for the "Lychi Resolver" Node. This node uses the LyChI
 * library (https://github.com/ncats/lychi) developed by the Informatics Group
 * at the NCATS/NIH to convert SMILES into LyChI identifier.
 *
 * @author Vishal Siramshetty (siramshettyv2@nih.gov)
 */
public class LychiResolverNodeView extends NodeView<LychiResolverNodeModel> {

	/**
	 * Creates a new view.
	 * 
	 * @param nodeModel The model (class: {@link LychiResolverNodeModel})
	 */
	protected LychiResolverNodeView(final LychiResolverNodeModel nodeModel) {
		super(nodeModel);

		// TODO instantiate the components of the view here.

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void modelChanged() {

		// TODO retrieve the new model from your nodemodel and
		// update the view.
		LychiResolverNodeModel nodeModel = (LychiResolverNodeModel) getNodeModel();
		assert nodeModel != null;

		// be aware of a possibly not executed nodeModel! The data you retrieve
		// from your nodemodel could be null, emtpy, or invalid in any kind.

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onClose() {

		// TODO things to do when closing the view
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onOpen() {

		// TODO things to do when opening the view
	}

}
