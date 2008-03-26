/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ui.internal.viewer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.impl.result.Filter;
import org.eclipse.mat.impl.result.RefinedStructuredResult;
import org.eclipse.mat.impl.result.TotalsRow;
import org.eclipse.mat.impl.result.RefinedStructuredResult.CalculationJobDefinition;
import org.eclipse.mat.impl.test.IOutputter;
import org.eclipse.mat.impl.test.OutputterRegistry;
import org.eclipse.mat.impl.test.ResultRenderer.RenderingInfo;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.Copy;
import org.eclipse.mat.ui.util.EasyToolBarDropDown;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ImageHelper;
import org.eclipse.mat.ui.util.PopupMenu;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.ui.util.QueryContextMenu;
import org.eclipse.mat.ui.util.SearchOnTyping;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.themes.ColorUtil;


public abstract class RefinedResultViewer
{
    protected static final int LIMIT = 25;
    protected static final int MAX_COLUMN_WIDTH = 500;
    protected static final int MIN_COLUMN_WIDTH = 90;

    private static final String[] TXT_FILTER_NAMES = { "Plain Text(*.txt)" }; //$NON-NLS-1$
    private static final String[] HTML_FILTER_NAMES = { "Web Page (*.html)" }; //$NON-NLS-1$

    // These filter extensions are used to filter which files are displayed.
    private static final String[] TXT_FILTER_EXTS = { "*.txt" }; //$NON-NLS-1$
    private static final String[] HTML_FILTER_EXTS = { "*.html" }; //$NON-NLS-1$

    /* package */interface Key
    {
        String CONTROL = "$control";
    }

    /* package */static class ControlItem
    {
        public ControlItem(boolean expandAndSelect, int level)
        {
            this.expandAndSelect = expandAndSelect;
            this.level = level;
        }

        boolean expandAndSelect;
        int level;

        List<?> children;
        TotalsRow totals;
        boolean hasBeenPainted;

        @Override
        public String toString()
        {
            return level + " " + hashCode() + " " + totals;
        }

    }

    /* package */interface WidgetAdapter
    {
        Composite createControl(Composite parent);

        Item createColumn(Column column, int ii, SelectionListener selectionListener);

        ControlEditor createEditor();

        void setEditor(Composite composite, Item item, int columnIndex);

        Item[] getSelection();

        int indexOf(Item item);

        Item getItem(Item item, int index);

        Item getParentItem(Item item);

        Item getItem(Point pt);

        Rectangle getBounds(Item item, int columnIndex);

        Rectangle getImageBounds(Item item, int columnIndex);

        void apply(Item item, int index, String label, Color color, Font font);

        void apply(Item item, int index, String label);

        void apply(Item item, Font font);

        Font getFont();

        Item getSortColumn();

        int getSortDirection();

        void setSortColumn(Item column);

        void setSortDirection(int direction);

        void setItemCount(Item item, int count);

        int getItemCount(Item object);

        void setExpanded(Item parentItem, boolean b);

        Rectangle getTextBounds(Widget item, int index);
    }

    /** pane in which the viewer is embedded */
    protected HeapEditor editor;

    /** the editor pane */
    protected AbstractEditorPane pane;

    /** adapter hiding specifics of table or tree */
    protected WidgetAdapter adapter;

    /** load SWT resources */
    protected LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

    /** the control (either tree or table) */
    protected Composite control;

    /** the columns (either tree or table) */
    protected Item[] columns;

    /** the editor (either tree or table) */
    protected ControlEditor controlEditor;

    /** number of items visible in the window */
    protected int visibleItemsEstimate;

    /** fonts & colors for filter & total row */
    protected Font boldFont;
    protected Color grayColor;
    protected Color greenColor;

    /** fonts used for decorated columns */
    protected Font[] fonts;
    /** colors used for decorated columns */
    protected Color[] colors;

    protected QueryResult queryResult;
    protected RefinedStructuredResult result;

    /** details a/b retained size calculation */
    protected List<CalculationJobDefinition> jobs;

    protected QueryContextMenu contextMenu;
    protected TotalsRow rootTotalsRow;
    protected boolean needsPacking = true;

    // //////////////////////////////////////////////////////////////
    // initialization
    // //////////////////////////////////////////////////////////////

    /* package */RefinedResultViewer(QueryResult result, RefinedStructuredResult refinedResult)
    {
        this.queryResult = result;
        this.result = refinedResult;
        this.jobs = new ArrayList<CalculationJobDefinition>(refinedResult.getJobs());
    }

    public abstract void init(Composite parent, HeapEditor editor, AbstractEditorPane pane);

    protected void init(WidgetAdapter viewer, Composite parent, HeapEditor editor, AbstractEditorPane pane)
    {
        this.adapter = viewer;
        this.editor = editor;
        this.pane = pane;

        parent.setRedraw(false);
        try
        {
            control = adapter.createControl(parent);

            boldFont = resourceManager.createFont(FontDescriptor.createFrom(adapter.getFont()).setStyle(SWT.BOLD));
            grayColor = resourceManager.createColor( //
                            ColorUtil.blend(control.getBackground().getRGB(), control.getForeground().getRGB()));
            greenColor = control.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);

            createColumns();
            addPaintListener();
            addDoubleClickListener();
            createContextMenu();

            if (result.getSortColumn() >= 0)
            {
                adapter.setSortColumn(columns[result.getSortColumn()]);
                adapter.setSortDirection(result.getSortDirection().getSwtCode());
            }

            SearchOnTyping.attachTo(control, 0);

            // estimate the number of visible items
            // (control is invisible! -> no actual size available)
            int estimatedLineHeight = Platform.OS_MACOSX.equals(Platform.getOS()) ? 20 : 18;

            Rectangle bounds = parent.getParent().getBounds();
            visibleItemsEstimate = Math.max(((bounds.height - 10) / estimatedLineHeight) - 2, 1);

            refresh(true);

            addTextEditors();
        }
        finally
        {
            parent.setRedraw(true);
        }
    }

    private void createColumns()
    {
        Column[] queryColumns = result.getColumns();
        int nrOfColumns = queryColumns.length;

        columns = new Item[nrOfColumns];
        for (int ii = 0; ii < nrOfColumns; ++ii)
        {
            Column queryColumn = queryColumns[ii];
            columns[ii] = adapter.createColumn(queryColumn, ii, new ColumnSelectionListener());

            if (ii == 0)
            {
                columns[ii].addListener(SWT.Resize, new Listener()
                {
                    public void handleEvent(Event event)
                    {
                        control.redraw();
                    }
                });
            }
        }
    }

    private void addDoubleClickListener()
    {
        control.addListener(SWT.DefaultSelection, new Listener()
        {
            public void handleEvent(Event event)
            {
                Item widget = (Item) event.item;
                Object data = widget.getData();
                if (data != null)
                    return;

                ControlItem ctrl = null;

                Item parent = adapter.getParentItem(widget);
                if (parent == null)
                {
                    if (adapter.indexOf(widget) != 0)
                        ctrl = (ControlItem) control.getData(Key.CONTROL);
                }
                else
                {
                    ctrl = (ControlItem) parent.getData(Key.CONTROL);
                }

                if (ctrl == null || ctrl.totals == null)
                    return;

                if (ctrl.totals.getVisibleItems() >= ctrl.totals.getNumberOfItems())
                    return;

                doRevealChildren(parent, false);

                event.doit = false;
            }
        });
    }

    protected abstract List<?> getElements(Object parent);

    protected void applyTextAndImage(Item item, Object element)
    {
        item.setData(element);
        adapter.apply(item, adapter.getFont());

        URL image = result.getIcon(element);
        if (image != null)
            item.setImage(ImageHelper.getImage(image));

        for (int ii = 0; ii < columns.length; ii++)
        {
            if (!result.isDecorated(ii))
            {
                adapter.apply(item, ii, result.getFormattedColumnValue(element, ii));
            }
            else
            {
                String[] texts = new String[3];
                texts[0] = result.getColumns()[ii].getDecorator().prefix(element);
                texts[1] = result.getFormattedColumnValue(element, ii);
                texts[2] = result.getColumns()[ii].getDecorator().suffix(element);
                item.setData(String.valueOf(ii), texts);
                adapter.apply(item, ii, asString(texts));
            }
        }
    }

    protected void applyTotals(Item item, TotalsRow totalsRow)
    {
        item.setImage(ImageHelper.getImage(totalsRow.getIcon()));

        for (int ii = 0; ii < columns.length; ii++)
            adapter.apply(item, ii, totalsRow.getLabel(ii));

        adapter.apply(item, boldFont);

        item.setData(null);
        item.setData(Key.CONTROL, null);
    }

    protected void applyUpdating(Item item)
    {
        item.setText("updating...");
        item.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.REFRESHING));
        item.setData(null);
        item.setData(Key.CONTROL, null);
    }

    protected void applyFilterData(Item item)
    {
        Filter[] filter = result.getFilter();
        for (int ii = 0; ii < filter.length; ii++)
            applyFilterData(item, ii, filter[ii]);
    }

    protected void applyFilterData(Item item, int columnIndex, Filter filter)
    {
        if (columnIndex == 0)
            item.setImage(MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.FILTER));

        String label = filter.isActive() ? filter.getCriteria() : filter.getLabel();
        Color color = filter.isActive() ? greenColor : grayColor;
        Font font = filter.isActive() ? boldFont : adapter.getFont();
        adapter.apply(item, columnIndex, label, color, font);
    }

    protected String asString(String[] texts)
    {
        StringBuilder buf = new StringBuilder();
        for (int ii = 0; ii < texts.length; ii++)
        {
            if (texts[ii] != null)
            {
                if (buf.length() > 0)
                    buf.append(" ");
                buf.append(texts[ii]);
            }
        }
        return buf.toString();
    }

    private void addPaintListener()
    {
        boolean isDecorated = false;
        for (int ii = 0; !isDecorated && ii < result.getColumns().length; ii++)
            isDecorated = result.isDecorated(ii);

        if (!isDecorated)
            return;

        // fonts
        fonts = new Font[3];
        fonts[0] = boldFont; // prefix
        fonts[1] = adapter.getFont(); // normal
        fonts[2] = boldFont; // suffix

        // colors
        colors = new Color[3];
        colors[0] = null;
        colors[1] = null;
        colors[2] = grayColor;

        control.addListener(SWT.MeasureItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts == null)
                    return;

                if (texts.length > 0)
                {
                    int width = 0;
                    int height = 0;

                    Image image = ((Item) event.item).getImage();
                    if (image != null)
                        width += image.getBounds().width + 4;

                    for (int ii = 0; ii < texts.length; ii++)
                    {
                        if (texts[ii] != null)
                        {
                            event.gc.setFont(fonts[ii]);
                            Point size = event.gc.textExtent(texts[ii]);
                            width += size.x + 4;
                            height = Math.max(event.height, size.y + 2);
                        }
                    }

                    event.width = width;
                    event.height = height;
                }
                else
                {
                    event.height = Math.max(event.height, 16 + 1);
                    event.width = MIN_COLUMN_WIDTH;
                }

                event.doit = false;
            }
        });

        control.addListener(SWT.EraseItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts != null)
                {
                    event.detail &= ~SWT.FOREGROUND;
                }
            }
        });

        control.addListener(SWT.PaintItem, new Listener()
        {
            public void handleEvent(final Event event)
            {
                if (!result.isDecorated(event.index))
                    return;

                Object element = event.item.getData();
                if (element == null)
                    return;

                String[] texts = (String[]) event.item.getData(String.valueOf(event.index));
                if (texts != null)
                {
                    boolean isSelected = (event.detail & SWT.SELECTED) != 0;

                    if (isSelected)
                    {
                        Rectangle r = event.gc.getClipping();
                        event.gc.fillRectangle(event.x, event.y, r.width, event.height - 1);
                    }

                    int x = event.x;
                    Image image = ((Item) event.item).getImage();
                    if (image != null)
                    {
                        event.gc.drawImage(image, event.x + 1, event.y);
                        x += image.getBounds().width + 4;
                    }

                    Color fg = event.gc.getForeground();

                    for (int ii = 0; ii < texts.length; ii++)
                    {
                        if (texts[ii] != null)
                        {
                            event.gc.setFont(fonts[ii]);
                            if (!isSelected)
                                event.gc.setForeground(colors[ii] != null ? colors[ii] : fg);

                            Point size = event.gc.textExtent(texts[ii]);
                            event.gc.drawText(texts[ii], x + 1, event.y + Math.max(0, (event.height - size.y) / 2),
                                            true);
                            x += size.x + 4;
                        }
                    }

                    event.gc.setForeground(fg);

                    event.doit = false;
                }

            }

        });

    }

    private void createContextMenu()
    {
        contextMenu = new QueryContextMenu(editor, queryResult)
        {
            @Override
            protected void customMenu(PopupMenu menu, List<IContextObject> menuContext, final ContextProvider provider,
                            String label)
            {
                menu.addSeparator();

                Action action = new Action(Messages.label_calc_min_retained_size)
                {
                    @Override
                    public void run()
                    {
                        doCalculatedRetainedSizesForSelection(provider, true);
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                menu.add(action);

                action = new Action(Messages.label_calc_precise_retained_size)
                {
                    @Override
                    public void run()
                    {
                        doCalculatedRetainedSizesForSelection(provider, false);
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                menu.add(action);
            }

        };
    }

    private void addTextEditors()
    {
        controlEditor = adapter.createEditor();

        control.addListener(SWT.MouseDown, new Listener()
        {
            public void handleEvent(Event event)
            {
                Point pt = new Point(event.x, event.y);

                Item item = adapter.getItem(pt);
                if (item != adapter.getItem(null, 0))
                    return;

                int columnIndex = getColumnIndex(item, pt);
                if (columnIndex < 0)
                    return;

                Filter filter = result.getFilter()[columnIndex];

                activateEditor(item, filter, columnIndex);
            }

            private int getColumnIndex(Item item, Point pt)
            {
                for (int ii = 0; ii < columns.length; ii++)
                {
                    Rectangle bounds = adapter.getBounds(item, ii);
                    if (bounds.contains(pt))
                        return ii;
                }
                return -1;
            }
        });
    }

    // //////////////////////////////////////////////////////////////
    // tool bar / context menu handling
    // //////////////////////////////////////////////////////////////

    public void contributeToToolBar(IToolBarManager manager)
    {
        editor.getEditorSite().getActionBars().setGlobalActionHandler(ActionFactory.COPY.getId(), new Action()
        {

            @Override
            public void run()
            {
                Copy.copyToClipboard(control);
            }

        });

        Action calculateRetainedSizeMenu = new EasyToolBarDropDown("Calculate retained size", //
                        MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR), //
                        editor)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                List<ContextProvider> providers = new ArrayList<ContextProvider>();

                List<ContextProvider> p = result.getResultMetaData().getContextProviders();
                if (p != null && !p.isEmpty())
                    providers.addAll(p);
                else
                    providers.add(queryResult.getDefaultContextProvider());

                if (!providers.isEmpty())
                {
                    for (ContextProvider cp : providers)
                    {
                        PopupMenu toThisMenu = menu;
                        if (providers.size() > 1)
                        {
                            PopupMenu subMenu = new PopupMenu(cp.getLabel());
                            menu.add(subMenu);
                            toThisMenu = subMenu;
                        }

                        addRetainedSizeActions(toThisMenu, cp);
                    }
                }
            }

            private void addRetainedSizeActions(PopupMenu toThisMenu, final ContextProvider cp)
            {
                Action action = new Action(Messages.label_calc_min_retained_size)
                {
                    @Override
                    public void run()
                    {
                        doCalculateRetainedSizesForAll(cp, true);
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                toThisMenu.add(action);

                action = new Action(Messages.label_calc_precise_retained_size)
                {
                    @Override
                    public void run()
                    {
                        doCalculateRetainedSizesForAll(cp, false);
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.CALCULATOR));
                toThisMenu.add(action);

            }
        };

        Action exportMenu = new EasyToolBarDropDown("Export", MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_MENU), //
                        editor)
        {

            @Override
            public void contribute(PopupMenu menu)
            {
                Action action = new Action("Export to HTML...")
                {
                    @Override
                    public void run()
                    {
                        ExportDialog dialog = new ExportDialog(control.getShell(), HTML_FILTER_NAMES, HTML_FILTER_EXTS);
                        String fileName = dialog.open();
                        try
                        {
                            if (fileName != null)
                            {
                                IOutputter outputter = OutputterRegistry.instance().get("html");
                                PrintWriter writer = new PrintWriter(new FileWriter(fileName));
                                RenderingInfo rInfo = new RenderingInfo(result.getColumns().length);
                                outputter.process(null, null, result, rInfo, writer);
                                writer.flush();
                                writer.close();
                            }
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException("Error in export to HTML: data type not supported for export.",
                                            e);
                        }
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_HTML));
                // TODO(en) implement export to HTML
                // menu.add(action);

                action = new Action("Export to CSV...")
                {
                    @Override
                    public void run()
                    {
                        ExportDialog dialog = new ExportDialog(control.getShell());
                        String fileName = dialog.open();
                        try
                        {
                            if (fileName != null)
                            {
                                IOutputter outputter = OutputterRegistry.instance().get("csv");
                                PrintWriter writer = new PrintWriter(new FileWriter(fileName));
                                RenderingInfo rInfo = new RenderingInfo(result.getColumns().length);
                                outputter.process(null, null, result, rInfo, writer);
                                writer.flush();
                                writer.close();
                            }
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException("Error in export to .csv: data type not supported for export.",
                                            e);
                        }
                    }
                };

                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_CSV));
                menu.add(action);

                action = new Action("Export to TXT...")
                {
                    public void run()
                    {
                        ExportDialog dialog = new ExportDialog(control.getShell(), TXT_FILTER_NAMES, TXT_FILTER_EXTS);
                        String fileName = dialog.open();
                        if (fileName != null)
                        {
                            Copy.exportToTxtFile(control, fileName);
                        }
                    }
                };
                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXPORT_TXT));
                menu.add(action);

            }
        };

        manager.add(calculateRetainedSizeMenu);
        manager.add(exportMenu);
    }

    public void addContextMenu(PopupMenu menu)
    {
        contextMenu.addContextActions(menu, getSelection());
        addFilterMenu(menu);
        addMoreMenu(menu);
    }

    private void addFilterMenu(PopupMenu menu)
    {
        menu.addSeparator();

        PopupMenu filterMenu = new PopupMenu("Edit filter");
        menu.add(filterMenu);

        for (int ii = 0; ii < columns.length; ii++)
        {
            final int columnIndex = ii;

            Action action = new Action(columns[ii].getText())
            {
                @Override
                public void run()
                {
                    Item item = adapter.getItem(null, 0);
                    Filter filter = result.getFilter()[columnIndex];
                    activateEditor(item, filter, columnIndex);
                }
            };
            filterMenu.add(action);
        }
    }

    private void addMoreMenu(PopupMenu menu)
    {
        Item[] selection = adapter.getSelection();
        if (selection.length != 1)
            return;

        if (selection[0].getData() != null)
            return;

        ControlItem ctrl = null;

        final Item parent = adapter.getParentItem(selection[0]);
        if (parent == null)
        {
            if (adapter.indexOf(selection[0]) != 0)
                ctrl = (ControlItem) control.getData(Key.CONTROL);
        }
        else
        {
            ctrl = (ControlItem) parent.getData(Key.CONTROL);
        }

        if (ctrl != null && ctrl.totals != null //
                        && ctrl.totals.getVisibleItems() < ctrl.totals.getNumberOfItems())
        {
            menu.addSeparator();

            boolean isRest = ctrl.totals.getNumberOfItems() - ctrl.totals.getVisibleItems() <= LIMIT;

            if (!isRest)
            {
                Action action = new Action("Next 25")
                {
                    @Override
                    public void run()
                    {
                        doRevealChildren(parent, false);
                    }

                };
                action.setImageDescriptor(MemoryAnalyserPlugin
                                .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
                menu.add(action);
            }

            Action action = new Action("Expand All")
            {
                @Override
                public void run()
                {
                    doRevealChildren(parent, true);
                }

            };
            action.setImageDescriptor(MemoryAnalyserPlugin.getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.PLUS));
            menu.add(action);
        }
    }

    private final void doRevealChildren(Item parent, boolean all)
    {
        if (parent != null && parent.isDisposed())
            return;

        ControlItem ctrl = (ControlItem) (parent == null ? control.getData(Key.CONTROL) : parent.getData(Key.CONTROL));
        if (ctrl == null || ctrl.totals == null)
            return;

        int visible = all ? ctrl.totals.getNumberOfItems() : Math.min(ctrl.totals.getVisibleItems() + LIMIT,
                        ctrl.totals.getNumberOfItems());

        if (visible - ctrl.totals.getVisibleItems() > 5000)
        {
            MessageBox box = new MessageBox(control.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
            box.setMessage(MessageFormat.format("You are about to expand {0} elements.\n" //
                            + "This can BLOCK YOUR UI for some time.\n" //
                            + "Continue?", //
                            (visible - ctrl.totals.getVisibleItems())));
            if (box.open() != SWT.OK)
                return;
        }

        ctrl.totals.setVisibleItems(visible);

        control.getParent().setRedraw(false);

        try
        {
            widgetRevealChildren(parent, ctrl.totals);
        }
        finally
        {
            control.getParent().setRedraw(true);
        }
    }

    protected abstract void widgetRevealChildren(Item parent, TotalsRow totalsData);

    private void activateEditor(final Item item, final Filter filter, final int columnIndex)
    {
        boolean showBorder = false;
        final Composite composite = new Composite(control, SWT.NONE);
        final Text text = new Text(composite, SWT.NONE);
        final int inset = showBorder ? 1 : 0;

        composite.addListener(SWT.Resize, new Listener()
        {
            public void handleEvent(Event e)
            {
                Rectangle rect = composite.getClientArea();
                text.setBounds(rect.x + inset, rect.y + inset, rect.width - inset * 2, rect.height - inset * 2);
            }
        });

        Listener textListener = new Listener()
        {
            public void handleEvent(final Event e)
            {
                switch (e.type)
                {
                    case SWT.FocusOut:
                        updateCriteria(item, filter, columnIndex, text.getText());
                        composite.dispose();
                        break;

                    case SWT.Verify:
                        Rectangle cell = adapter.getBounds(item, columnIndex);
                        Rectangle image = adapter.getImageBounds(item, columnIndex);

                        controlEditor.minimumHeight = cell.height;
                        controlEditor.minimumWidth = cell.width - image.width;
                        controlEditor.layout();
                        break;

                    case SWT.Traverse:
                        switch (e.detail)
                        {
                            case SWT.TRAVERSE_RETURN:
                                // $JL-SWITCH$ fall through
                                updateCriteria(item, filter, columnIndex, text.getText());
                            case SWT.TRAVERSE_ESCAPE:
                                composite.dispose();
                                e.doit = false;
                        }
                        break;
                }
            }

            private void updateCriteria(final Item filterRow, final Filter filter, final int columnIndex, String text)
            {
                boolean changed = false;
                try
                {
                    changed = filter.setCriteria(text);

                }
                catch (IllegalArgumentException e)
                {
                    ErrorHelper.showErrorMessage(e);
                }

                if (changed)
                {
                    applyFilterData(item, columnIndex, filter);
                    refresh(false);
                }
            }
        };

        text.addListener(SWT.FocusOut, textListener);
        text.addListener(SWT.Traverse, textListener);
        text.addListener(SWT.Verify, textListener);

        adapter.setEditor(composite, item, columnIndex);

        text.setText(filter.getCriteria() != null ? filter.getCriteria() : "");
        text.selectAll();
        text.setFocus();

    }

    // //////////////////////////////////////////////////////////////
    // retained size calculation for all/selection
    // //////////////////////////////////////////////////////////////

    public void showRetainedSizeColumn(ContextProvider provider)
    {
        prepareColumns(provider);
    }

    protected void prepareColumns(ContextProvider provider)
    {
        Column queryColumn = result.getColumnFor(provider);
        if (queryColumn == null)
        {
            queryColumn = result.addRetainedSizeColumn(provider);

            Item column = adapter.createColumn(queryColumn, this.columns.length, new ColumnSelectionListener());

            Item[] copy = new Item[columns.length + 1];
            System.arraycopy(columns, 0, copy, 0, columns.length);
            copy[columns.length] = column;
            columns = copy;

            applyFilterData(adapter.getItem(null, 0));
        }
    }

    protected void doCalculateRetainedSizesForAll(ContextProvider provider, boolean approximation)
    {
        prepareColumns(provider);

        boolean jobFound = false;
        for (CalculationJobDefinition job : jobs)
        {
            if (job.getContextProvider().hasSameTarget(provider))
            {
                jobFound = true;
                if (!approximation && job.isApproximation())
                    job.setApproximation(false);
            }
        }

        if (!jobFound)
            jobs.add(new CalculationJobDefinition(provider, approximation));

        ControlItem ctrl = (ControlItem) control.getData(Key.CONTROL);
        new RetainedSizeJob.OnFullList(this, provider, ctrl.children, approximation, null, ctrl).schedule();
    }

    protected void doCalculatedRetainedSizesForSelection(ContextProvider provider, boolean approximation)
    {
        prepareColumns(provider);

        Item[] items = adapter.getSelection();
        if (items.length == 0)
            return;

        List<Item> widgetItems = new ArrayList<Item>();
        List<Object> subjectItems = new ArrayList<Object>();

        for (Item tItem : items)
        {
            Object subject = tItem.getData();
            if (subject != null)
            {
                widgetItems.add(tItem);
                subjectItems.add(subject);
            }
        }

        if (widgetItems.size() > 0)
        {
            new RetainedSizeJob.OnSelection(this, provider, subjectItems, approximation, widgetItems).schedule();
        }
    }

    // //////////////////////////////////////////////////////////////
    // manipulation
    // //////////////////////////////////////////////////////////////

    public RefinedStructuredResult getResult()
    {
        return result;
    }

    public QueryResult getQueryResult()
    {
        return queryResult;
    }

    public final Control getControl()
    {
        return control;
    }

    public final IStructuredSelection getSelection()
    {
        Item[] items = adapter.getSelection();
        if (items.length == 0)
            return StructuredSelection.EMPTY;

        List<Object> selection = new ArrayList<Object>(items.length);
        for (int ii = 0; ii < items.length; ii++)
        {
            Object row = items[ii].getData();
            if (row != null)
                selection.add(row);
        }

        return new StructuredSelection(selection);
    }

    protected final void resort()
    {
        List<?> children = ((ControlItem) control.getData(Key.CONTROL)).children;
        new SortingJob(this, children).schedule();
    }

    protected abstract void refresh(boolean expandAndSelect);

    protected abstract void doUpdateChildren(Item parentItem, ControlItem ctrl);

    public void dispose()
    {
        control.dispose();
        resourceManager.dispose();
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private final class ColumnSelectionListener implements SelectionListener
    {
        public void widgetDefaultSelected(SelectionEvent e)
        {}

        public void widgetSelected(SelectionEvent e)
        {
            Item treeColumn = (Item) e.widget;
            Column queryColumn = (Column) treeColumn.getData();

            boolean isSorted = treeColumn == adapter.getSortColumn();

            int direction = SWT.UP;

            if (isSorted)
                direction = adapter.getSortDirection() == SWT.UP ? SWT.DOWN : SWT.UP;
            else
                direction = queryColumn.isNumeric() ? SWT.DOWN : SWT.UP;

            control.getParent().setRedraw(false);

            try
            {
                adapter.setSortColumn(treeColumn);
                adapter.setSortDirection(direction);

                result.setSortOrder(queryColumn, Column.SortDirection.of(direction));

                resort();
            }
            finally
            {
                control.getParent().setRedraw(true);
            }
        }
    }

    // //////////////////////////////////////////////////////////////
    // jobs
    // //////////////////////////////////////////////////////////////

    protected static class RetrieveChildrenJob extends AbstractPaneJob implements ISchedulingRule
    {
        private RefinedResultViewer viewer;
        private ControlItem ctrl;
        private Item parentItem;
        private Object parent;

        protected RetrieveChildrenJob(RefinedResultViewer viewer, ControlItem ctrl, Item parentItem, Object parent)
        {
            super("Retrieving view elements...", viewer.pane);

            this.viewer = viewer;
            this.ctrl = ctrl;
            this.parentItem = parentItem;
            this.parent = parent;

            setUser(true);
            setRule(this);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            try
            {
                loadElements();
                updateDisplay();
                calculateTotals(monitor);

                for (RefinedStructuredResult.CalculationJobDefinition job : viewer.jobs)
                {
                    new RetainedSizeJob.OnFullList(viewer, job.getContextProvider(), ctrl.children, job
                                    .isApproximation(), parentItem, ctrl).schedule();
                }

                return Status.OK_STATUS;
            }
            catch (RuntimeException e)
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (viewer.control.isDisposed())
                            return;

                        viewer.control.getParent().setRedraw(false);
                        try
                        {
                            if (parentItem != null)
                            {
                                parentItem.setData(Key.CONTROL, null);
                                viewer.adapter.setItemCount(parentItem, 1);
                                viewer.adapter.setExpanded(parentItem, false);
                            }
                            else
                            {
                                viewer.refresh(false);
                            }
                        }
                        finally
                        {
                            viewer.control.getParent().setRedraw(true);
                        }
                    }
                });

                if (e instanceof IProgressListener.OperationCanceledException)
                    return Status.CANCEL_STATUS;
                else
                    return ErrorHelper.createErrorStatus(e);
            }
        }

        private void calculateTotals(IProgressMonitor monitor)
        {
            if (monitor.isCanceled())
                return;

            boolean hasChildren = ctrl.totals.getNumberOfItems() > 0 || ctrl.totals.getFilteredItems() > 0;
            if (hasChildren && ctrl.children.size() > 1)
            {
                viewer.result.calculateTotals(ctrl.children, ctrl.totals, new ProgressMonitorWrapper(monitor));

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        if (viewer.control.isDisposed())
                            return;

                        viewer.control.getParent().setRedraw(false);
                        try
                        {
                            if (parentItem == null) // root elements
                            {
                                int index = viewer.adapter.getItemCount(null) - 1;
                                Item item = viewer.adapter.getItem(null, index);
                                updateItem(item, (ControlItem) viewer.control.getData(Key.CONTROL));
                            }
                            else
                            {
                                if (parentItem.isDisposed())
                                    return;

                                int index = viewer.adapter.getItemCount(parentItem) - 1;
                                Item item = viewer.adapter.getItem(parentItem, index);
                                updateItem(item, (ControlItem) parentItem.getData(Key.CONTROL));
                            }
                        }
                        finally
                        {
                            viewer.control.getParent().setRedraw(true);
                        }
                    }

                    private void updateItem(Item item, ControlItem ctrl)
                    {
                        if (item.isDisposed())
                            return;

                        viewer.applyTotals(item, ctrl.totals);
                    }
                });
            }
        }

        private void updateDisplay()
        {
            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
            {
                public void run()
                {
                    if (viewer.control.isDisposed())
                        return;

                    viewer.control.getParent().setRedraw(false);
                    try
                    {
                        viewer.doUpdateChildren(parentItem, ctrl);
                    }
                    finally
                    {
                        viewer.control.getParent().setRedraw(true);
                    }
                }
            });
        }

        private void loadElements()
        {
            if (ctrl == null)
                ctrl = new ControlItem(false, 0);

            ctrl.children = viewer.getElements(parent);
            ctrl.totals = viewer.result.buildTotalsRow(ctrl.children);

            if (parent != null)
            {
                ctrl.totals.setVisibleItems(Math.min(LIMIT, ctrl.totals.getNumberOfItems()));
            }
            else
            {
                if (viewer.rootTotalsRow != null
                                && viewer.rootTotalsRow.getVisibleItems() > ctrl.totals.getVisibleItems())
                {
                    ctrl.totals.setVisibleItems(Math.min(ctrl.totals.getNumberOfItems(), viewer.rootTotalsRow
                                    .getVisibleItems()));
                }
                else
                {
                    ctrl.totals.setVisibleItems(Math.min(viewer.visibleItemsEstimate, ctrl.totals.getNumberOfItems()));
                }
            }

            if (parent == null)
                viewer.rootTotalsRow = ctrl.totals;
        }

        public boolean contains(ISchedulingRule rule)
        {
            return rule.getClass() == getClass();
        }

        public boolean isConflicting(ISchedulingRule rule)
        {
            return rule.getClass() == getClass();
        }
    }

    private static class SortingJob extends AbstractPaneJob
    {
        RefinedResultViewer viewer;
        List<?> list;

        private SortingJob(RefinedResultViewer viewer, List<?> list)
        {
            super("Sorting...", viewer.pane);
            this.viewer = viewer;
            this.list = list;

            setUser(true);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            viewer.result.sort(list);

            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
            {
                public void run()
                {
                    if (viewer.control.isDisposed())
                        return;

                    try
                    {
                        viewer.control.getParent().setRedraw(false);
                        viewer.doUpdateChildren(null, (ControlItem) viewer.control.getData(Key.CONTROL));
                    }
                    finally
                    {
                        viewer.control.getParent().setRedraw(true);
                    }
                }
            });

            return Status.OK_STATUS;
        }

    }

}
