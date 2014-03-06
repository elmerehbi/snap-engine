/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.beam.visat.actions;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ConvolutionFilterBand;
import org.esa.beam.framework.datamodel.FilterBand;
import org.esa.beam.framework.datamodel.GeneralFilterBand;
import org.esa.beam.framework.datamodel.Kernel;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.visat.VisatApp;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.text.MessageFormat;

// todo - allow for user-defined kernels and structuring elements
// todo - add kernel and structuring element editors
// todo - import/export kernels and structuring elements

/**
 * Installs commands into VISAT which lets a user attach a {@link org.esa.beam.framework.datamodel.PixelGeoCoding} based on pixels rather than tie points to the current product.
 *
 * @author Norman Fomferra
 */
public class CreateFilteredBandAction extends ExecCommand {

    private static final String TITLE = "Create Filtered Band"; /*I18N*/


    @Override
    public void actionPerformed(CommandEvent event) {
        createFilteredBand();
    }

    @Override
    public void updateState(CommandEvent event) {
        final ProductNode node = VisatApp.getApp().getSelectedProductNode();
        event.getCommand().setEnabled(node instanceof Band);
    }

    Filter[] LINE_DETECTION_FILTERS = {
            new KernelFilter("Horizontal Edges", "he", new Kernel(3, 3, new double[]{
                    -1, -1, -1,
                    +2, +2, +2,
                    -1, -1, -1
            })),
            new KernelFilter("Vertical Edges", "ve", new Kernel(3, 3, new double[]{
                    -1, +2, -1,
                    -1, +2, -1,
                    -1, +2, -1
            })),
            new KernelFilter("Left Diagonal Edges", "lde", new Kernel(3, 3, new double[]{
                    +2, -1, -1,
                    -1, +2, -1,
                    -1, -1, +2
            })),
            new KernelFilter("Right Diagonal Edges", "rde", new Kernel(3, 3, new double[]{
                    -1, -1, +2,
                    -1, +2, -1,
                    +2, -1, -1
            })),

            new KernelFilter("Compass Edge Detector", "ced", new Kernel(3, 3, new double[]{
                    -1, +1, +1,
                    -1, -2, +1,
                    -1, +1, +1,
            })),

            new KernelFilter("Diagonal Compass Edge Detector", "dced", new Kernel(3, 3, new double[]{
                    +1, +1, +1,
                    -1, -2, +1,
                    -1, -1, +1,
            })),

            new KernelFilter("Roberts Cross North-West", "rcnw", new Kernel(2, 2, new double[]{
                    +1, 0,
                    0, -1,
            })),

            new KernelFilter("Roberts Cross North-East", "rcne", new Kernel(2, 2, new double[]{
                    0, +1,
                    -1, 0,
            })),
    };
    Filter[] GRADIENT_DETECTION_FILTERS = {
            new KernelFilter("Sobel North", "sn", new Kernel(3, 3, new double[]{
                    -1, -2, -1,
                    +0, +0, +0,
                    +1, +2, +1,
            })),
            new KernelFilter("Sobel South", "ss", new Kernel(3, 3, new double[]{
                    +1, +2, +1,
                    +0, +0, +0,
                    -1, -2, -1,
            })),
            new KernelFilter("Sobel West", "sw", new Kernel(3, 3, new double[]{
                    -1, 0, +1,
                    -2, 0, +2,
                    -1, 0, +1,
            })),
            new KernelFilter("Sobel East", "se", new Kernel(3, 3, new double[]{
                    +1, 0, -1,
                    +2, 0, -2,
                    +1, 0, -1,
            })),
            new KernelFilter("Sobel North East", "sne", new Kernel(3, 3, new double[]{
                    +0, -1, -2,
                    +1, +0, -1,
                    +2, +1, -0,
            })),
    };
    Filter[] SMOOTHING_FILTERS = {
            new KernelFilter("Arithmetic Mean 3x3", "am3", new Kernel(3, 3, 1.0 / 9.0, new double[]{
                    +1, +1, +1,
                    +1, +1, +1,
                    +1, +1, +1,
            })),

            new KernelFilter("Arithmetic Mean 4x4", "am4", new Kernel(4, 4, 1.0 / 16.0, new double[]{
                    +1, +1, +1, +1,
                    +1, +1, +1, +1,
                    +1, +1, +1, +1,
                    +1, +1, +1, +1,
            })),

            new KernelFilter("Arithmetic Mean 5x5", "am5", new Kernel(5, 5, 1.0 / 25.0, new double[]{
                    +1, +1, +1, +1, +1,
                    +1, +1, +1, +1, +1,
                    +1, +1, +1, +1, +1,
                    +1, +1, +1, +1, +1,
                    +1, +1, +1, +1, +1,
            })),

            new KernelFilter("Low-Pass 3x3", "lp3", new Kernel(3, 3, 1.0 / 16.0, new double[]{
                    +1, +2, +1,
                    +2, +4, +2,
                    +1, +2, +1,
            })),
            new KernelFilter("Low-Pass 5x5", "lp5", new Kernel(5, 5, 1.0 / 60.0, new double[]{
                    +1, +1, +1, +1, +1,
                    +1, +4, +4, +4, +1,
                    +1, +4, 12, +4, +1,
                    +1, +4, +4, +4, +1,
                    +1, +1, +1, +1, +1,
            })),
    };
    Filter[] SHARPENING_FILTERS = {
            new KernelFilter("High-Pass 3x3 #1", "hp31", new Kernel(3, 3, new double[]{
                    -1, -1, -1,
                    -1, +9, -1,
                    -1, -1, -1
            })),


            new KernelFilter("High-Pass 3x3 #2", "hp32", new Kernel(3, 3, new double[]{
                    +0, -1, +0,
                    -1, +5, -1,
                    +0, -1, +0
            })),

            new KernelFilter("High-Pass 5x5", "hp5", new Kernel(5, 5, new double[]{
                    +0, -1, -1, -1, +0,
                    -1, +2, -4, +2, -1,
                    -1, -4, 13, -4, -1,
                    -1, +2, -4, +2, -1,
                    +0, -1, -1, -1, +0,
            })),

    };
    Filter[] LAPLACIAN_FILTERS = {
            new KernelFilter("Laplace 3x3 (a)", "lap3a", new Kernel(3, 3, new double[]{
                    +0, -1, +0,
                    -1, +4, -1,
                    +0, -1, +0,
            })),
            new KernelFilter("Laplace 3x3 (b)", "lap3b", new Kernel(3, 3, new double[]{
                    -1, -1, -1,
                    -1, +8, -1,
                    -1, -1, -1,
            })),
            new KernelFilter("Laplace 5x5 (a)", "lap5a", new Kernel(5, 5, new double[]{
                    0, 0, -1, 0, 0,
                    0, -1, -2, -1, 0,
                    -1, -2, 16, -2, -1,
                    0, -1, -2, -1, 0,
                    0, 0, -1, 0, 0,
            })),
            new KernelFilter("Laplace 5x5 (b)", "lap5b", new Kernel(5, 5, new double[]{
                    -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1,
                    -1, -1, 24, -1, -1,
                    -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1,
            })),
    };

    Filter[] NON_LINEAR_FILTERS = {
            new GeneralFilter("Minimum 3x3", "min3", 3, GeneralFilterBand.OpType.MIN),
            new GeneralFilter("Minimum 5x5", "min5", 5, GeneralFilterBand.OpType.MIN),
            new GeneralFilter("Minimum 7x7", "min7", 5, GeneralFilterBand.OpType.MIN),
            new GeneralFilter("Maximum 3x3", "max3", 3, GeneralFilterBand.OpType.MAX),
            new GeneralFilter("Maximum 5x5", "max5", 5, GeneralFilterBand.OpType.MAX),
            new GeneralFilter("Maximum 7x7", "max7", 5, GeneralFilterBand.OpType.MAX),
            new GeneralFilter("Mean 3x3", "mean3", 3, GeneralFilterBand.OpType.MEAN),
            new GeneralFilter("Mean 5x5", "mean5", 5, GeneralFilterBand.OpType.MEAN),
            new GeneralFilter("Mean 7x7", "mean7", 5, GeneralFilterBand.OpType.MEAN),
            new GeneralFilter("Median 3x3", "median3", 3, GeneralFilterBand.OpType.MEDIAN),
            new GeneralFilter("Median 5x5", "median5", 5, GeneralFilterBand.OpType.MEDIAN),
            new GeneralFilter("Median 7x7", "median7", 5, GeneralFilterBand.OpType.MEDIAN),
            new GeneralFilter("Standard Deviation 3x3", "stddev3", 3, GeneralFilterBand.OpType.STDDEV),
            new GeneralFilter("Standard Deviation 5x5", "stddev5", 5, GeneralFilterBand.OpType.STDDEV),
            new GeneralFilter("Standard Deviation 7x7", "stddev7", 5, GeneralFilterBand.OpType.STDDEV),
    };

    Filter[] MORPHOLOGICAL_FILTERS = {
            new GeneralFilter("Erosion 3x3", "erode3", 3, GeneralFilterBand.OpType.EROSION),
            new GeneralFilter("Erosion 5x5", "erode5", 5, GeneralFilterBand.OpType.EROSION),
            new GeneralFilter("Erosion 7x7", "erode7", 5, GeneralFilterBand.OpType.EROSION),
            new GeneralFilter("Dilation 3x3", "dilate3", 3, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Dilation 5x5", "dilate5", 5, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Dilation 7x7", "dilate7", 5, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Opening 3x3", "open3", 3, GeneralFilterBand.OpType.EROSION, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Opening 5x5", "open5", 5, GeneralFilterBand.OpType.EROSION, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Opening 7x7", "open7", 5, GeneralFilterBand.OpType.EROSION, GeneralFilterBand.OpType.DILATION),
            new GeneralFilter("Closing 3x3", "close3", 3, GeneralFilterBand.OpType.DILATION, GeneralFilterBand.OpType.EROSION),
            new GeneralFilter("Closing 5x5", "close5", 5, GeneralFilterBand.OpType.DILATION, GeneralFilterBand.OpType.EROSION),
            new GeneralFilter("Closing 7x7", "close7", 5, GeneralFilterBand.OpType.DILATION, GeneralFilterBand.OpType.EROSION),
    };

    private void createFilteredBand() {
        final DialogData dialogData = promptForFilter();
        if (dialogData == null) {
            return;
        }
        final FilterBand filterBand = createFilterBand(dialogData.getFilter(), dialogData.getBandName());
        VisatApp visatApp = VisatApp.getApp();
        if (visatApp.getPreferences().getPropertyBool(VisatApp.PROPERTY_KEY_AUTO_SHOW_NEW_BANDS, true)) {
            visatApp.openProductSceneView(filterBand);
        }
    }

    private static FilterBand createFilterBand(Filter filter, String bandName) {
        RasterDataNode raster = (RasterDataNode) VisatApp.getApp().getSelectedProductNode();

        FilterBand filterBand;
        Product product = raster.getProduct();
        if (filter instanceof KernelFilter) {
            KernelFilter kernelFilter = (KernelFilter) filter;
            filterBand = new ConvolutionFilterBand(bandName, raster, kernelFilter.kernel);
        } else {
            RasterDataNode source = raster;
            GeneralFilter generalFilter = (GeneralFilter) filter;
            GeneralFilterBand.OpType[] opTypes = generalFilter.opTypes;
            for (int i = 0; i < opTypes.length - 1; i++) {
                GeneralFilterBand intermediateFilterBand = new GeneralFilterBand(bandName + "_im" + i, source, opTypes[i], generalFilter.size);
                intermediateFilterBand.setDescription(String.format("Intermediate filter band #%d for band '%s'",
                                                                    i,
                                                                    bandName));
                if (raster instanceof Band) {
                    ProductUtils.copySpectralBandProperties((Band) raster, intermediateFilterBand);
                }
                intermediateFilterBand.setNoDataValueUsed(false);
                product.addBand(intermediateFilterBand);
                source = intermediateFilterBand;
            }
            filterBand = new GeneralFilterBand(bandName, source, opTypes[opTypes.length - 1], generalFilter.size);
        }

        filterBand.setDescription(String.format("Filter '%s' applied to '%s'", filter.toString(), raster.getName()));
        if (raster instanceof Band) {
            ProductUtils.copySpectralBandProperties((Band) raster, filterBand);
        }
        product.addBand(filterBand);
        filterBand.fireProductNodeDataChanged();
        return filterBand;
    }

    private DialogData promptForFilter() {
        final JTree tree = createTree();
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final JScrollPane treeView = new JScrollPane(tree);

        final JPanel contentPane = new JPanel(new BorderLayout(4, 4));
        contentPane.add(new JLabel("Filters:"), BorderLayout.NORTH);
        contentPane.add(treeView, BorderLayout.CENTER);

        final ProductNode selectedNode = VisatApp.getApp().getSelectedProductNode();
        final Product product = selectedNode.getProduct();
        final JPanel namePanel = new JPanel(new BorderLayout(4, 4));
        namePanel.add(new JLabel("Band name:"), BorderLayout.WEST);     /*I18N*/
        final JTextField nameField = new JTextField(selectedNode.getName());
        namePanel.add(nameField, BorderLayout.CENTER);
        contentPane.add(namePanel, BorderLayout.SOUTH);

        tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                final Object lastPathComponent = e.getPath().getLastPathComponent();
                if (lastPathComponent instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) lastPathComponent;
                    Filter filter = (Filter) treeNode.getUserObject();
                    nameField.setText(selectedNode.getName() + "_" + filter.suffix);
                }
            }
        });

        final ModalDialog dialog = new CreateFilteredBandDialog(nameField, product, tree);
        dialog.setContent(contentPane);
        if (dialog.show() == ModalDialog.ID_OK) {
            return new DialogData(nameField.getText(), getSelectedFilter(tree));
        }
        return null;
    }

    private static class DialogData {

        private final Filter filter;
        private final String bandName;

        public DialogData(String bandName, Filter filter) {
            this.bandName = bandName;
            this.filter = filter;
        }

        public String getBandName() {
            return bandName;
        }

        public Filter getFilter() {
            return filter;
        }
    }

    private JTree createTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("@root");
        root.add(createNodes("Detect Lines", LINE_DETECTION_FILTERS));
        root.add(createNodes("Detect Gradients (Emboss)", GRADIENT_DETECTION_FILTERS));
        root.add(createNodes("Smooth and Blurr", SMOOTHING_FILTERS));
        root.add(createNodes("Sharpen", SHARPENING_FILTERS));
        root.add(createNodes("Enhance Discontinuities", LAPLACIAN_FILTERS));
        root.add(createNodes("Non-Linear Filters", NON_LINEAR_FILTERS));
        root.add(createNodes("Morphological Filters", MORPHOLOGICAL_FILTERS));
        final JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new MyDefaultTreeCellRenderer());
        tree.putClientProperty("JTree.lineStyle", "Angled");
        expandAll(tree);
        return tree;
    }

    private static Filter getSelectedFilter(final JTree tree) {
        final TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }
        final Object[] path = selectionPath.getPath();
        if (path != null && path.length > 0) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path[path.length - 1];
            if (treeNode.getUserObject() instanceof Filter) {
                return (Filter) treeNode.getUserObject();

            }
        }
        return null;
    }


    private static DefaultMutableTreeNode createNodes(String categoryName, Filter[] filters) {

        DefaultMutableTreeNode category = new DefaultMutableTreeNode(categoryName);

        for (Filter filter : filters) {
            DefaultMutableTreeNode item = new DefaultMutableTreeNode(filter);
            category.add(item);
        }

        return category;
    }


    private static void expandAll(JTree tree) {
        DefaultMutableTreeNode actNode = (DefaultMutableTreeNode) tree.getModel().getRoot();
        while (actNode != null) {
            if (!actNode.isLeaf()) {
                final TreePath actPath = new TreePath(actNode.getPath());
                tree.expandRow(tree.getRowForPath(actPath));
            }
            actNode = actNode.getNextNode();
        }
    }

    private static class MyDefaultTreeCellRenderer extends DefaultTreeCellRenderer {

        private Font plainFont;
        private Font boldFont;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            final JLabel c = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
                                                                         hasFocus);
            if (plainFont == null) {
                plainFont = c.getFont().deriveFont(Font.PLAIN);
                boldFont = c.getFont().deriveFont(Font.BOLD);
            }
            c.setFont(leaf ? plainFont : boldFont);
            c.setIcon(null);
            return c;
        }
    }

    private static abstract class Filter {

        final String name;
        final String suffix;

        public Filter(String name, String suffix) {
            this.name = name;
            this.suffix = suffix;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Filter filter = (Filter) o;

            if (!name.equals(filter.name)) return false;
            if (!suffix.equals(filter.suffix)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return name.hashCode() + 31 * suffix.hashCode();
        }
    }

    private static class KernelFilter extends Filter {

        private Kernel kernel;

        public KernelFilter(String name, String suffix, Kernel kernel) {
            super(name, suffix);
            this.kernel = kernel;
        }
    }

    private static class GeneralFilter extends Filter {

        final int size;
        final GeneralFilterBand.OpType[] opTypes;

        public GeneralFilter(String name, String suffix, int size, GeneralFilterBand.OpType... opTypes) {
            super(name, suffix);
            this.size = size;
            this.opTypes = opTypes;
        }

    }

    class CreateFilteredBandDialog extends ModalDialog {

        private final JTextField nameField;
        private final Product product;
        private final JTree tree;

        public CreateFilteredBandDialog(JTextField nameField, Product product, JTree tree) {
            super(VisatApp.getApp().getMainFrame(), CreateFilteredBandAction.TITLE, ModalDialog.ID_OK_CANCEL_HELP,
                  CreateFilteredBandAction.this.getHelpId());
            this.nameField = nameField;
            this.product = product;
            this.tree = tree;
        }

        @Override
        protected boolean verifyUserInput() {
            String message = null;
            final String bandName = nameField.getText().trim();
            if (bandName.equals("")) {
                message = "Please enter a name for the new filtered band."; /*I18N*/
            } else if (!ProductNode.isValidNodeName(bandName)) {
                message = MessageFormat.format("The band name ''{0}'' appears not to be valid.\n" +
                                               "Please choose a different band name.", bandName); /*I18N*/
            } else if (product.containsBand(bandName)) {
                message = MessageFormat.format("The selected product already contains a band named ''{0}''.\n" +
                                               "Please choose a different band name.", bandName); /*I18N*/
            } else if (getSelectedFilter(tree) == null) {
                message = "Please select a filter.";    /*I18N*/
            }
            if (message != null) {
                VisatApp.getApp().showErrorDialog(TITLE, message);
                return false;
            }
            return true;
        }
    }
}
