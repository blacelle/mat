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
package org.eclipse.mat.ui.internal.panes;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.impl.query.ArgumentDescriptor;
import org.eclipse.mat.impl.query.ArgumentSet;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.impl.query.QueryResult;
import org.eclipse.mat.inspections.query.OQLQuery;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.editor.MultiPaneEditorSite;
import org.eclipse.mat.ui.internal.query.ArgumentContextProvider;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;


public class OQLPane extends CompositeHeapEditorPane
{
    private StyledText queryString;

    private Action executeAction;

    // //////////////////////////////////////////////////////////////
    // initialization methods
    // //////////////////////////////////////////////////////////////

    public void createPartControl(Composite parent)
    {
        SashForm sash = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);

        queryString = new StyledText(sash, SWT.MULTI);
        queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        queryString.setText("/* Press F1 for help */");
        queryString.selectAll();
        queryString.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent e)
            {
                queryString.setStyleRanges(new StyleRange[0]);
            }
        });

        // TODO (en) [low] refactor once proper context sensitive help works

        queryString.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == SWT.F1)
                {
                    PlatformUI.getWorkbench().getHelpSystem().displayHelpResource(
                                    "/org.eclipse.mat.ui.help/html/46/9f7472517a52b6e10000000a155369/content.htm");
                    e.doit = false;
                }
                if (e.keyCode == '\r' && (e.stateMask & SWT.CTRL) != 0)
                {
                    executeAction.run();
                }
            }

        });

        createContainer(sash);

        sash.setWeights(new int[] { 1, 4 });

        makeActions();
        hookContextMenu();
    }

    private void makeActions()
    {
        executeAction = new Action()
        {
            @Override
            public void run()
            {
                try
                {
                    String query = queryString.getSelectionText();
                    Point queryRange = queryString.getSelectionRange();

                    if ("".equals(query))
                    {
                        query = queryString.getText();
                        queryRange = new Point(0, queryString.getCharCount());
                    }

                    try
                    {
                        // force parsing of OQL query
                        SnapshotFactory.createQuery(query);
                        new OQLJob(OQLPane.this, query).schedule();
                    }
                    catch (final OQLParseException e)
                    {
                        int start = findInText(query, e.getLine(), e.getColumn());

                        StyleRange style2 = new StyleRange();
                        style2.start = start + queryRange.x;
                        style2.length = queryRange.y - start;
                        style2.foreground = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_RED);
                        queryString.replaceStyleRanges(0, queryString.getCharCount(), new StyleRange[] { style2 });

                        createExceptionPane(e, query);
                    }
                    catch (Exception e)
                    {
                        createExceptionPane(e, query);
                    }
                }
                catch (PartInitException e1)
                {
                    ErrorHelper.logThrowableAndShowMessage(e1, "Error executing query");
                }
            }

        };
        executeAction.setText("Execute Query");
        executeAction.setImageDescriptor(MemoryAnalyserPlugin
                        .getImageDescriptor(MemoryAnalyserPlugin.ISharedImages.EXECUTE_QUERY));

       
        IWorkbenchWindow window = getEditorSite().getWorkbenchWindow();
        IWorkbenchAction globalAction = ActionFactory.COPY.create(window);
        Action copyAction = new Action()
        {

            @Override
            public void run()
            {
                queryString.copy();
            }

        };
        copyAction.setAccelerator(globalAction.getAccelerator());
        // Register copy action with the global action handler.
        IActionBars actionBars = getEditorSite().getActionBars();
        actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
    }

    protected int findInText(String query, int line, int column)
    {
        // index starts at 1
        // tabs count as 8

        int charAt = 0;

        while (line > 1)
        {
            while (charAt < query.length())
            {
                char c = query.charAt(charAt++);
                if (c == '\n')
                {
                    line--;
                    break;
                }
            }
        }

        while (column > 1 && charAt < query.length())
        {
            char c = query.charAt(charAt++);
            if (c == '\t')
                column -= 8;
            else
                column--;
        }

        return charAt;
    }

    private void hookContextMenu()
    {}

    @Override
    public void contributeToToolBar(IToolBarManager manager)
    {
        manager.add(executeAction);

        super.contributeToToolBar(manager);
    }

    @Override
    public void initWithArgument(final Object param)
    {
        if (param instanceof String)
        {
            queryString.setText((String) param);
            executeAction.run();
        }
        else if (param instanceof QueryResult)
        {
            QueryResult queryResult = (QueryResult) param;
            initQueryResult(queryResult);
        }
    }

    private void initQueryResult(QueryResult queryResult)
    {
        IOQLQuery.Result subject = (IOQLQuery.Result) (queryResult).getSubject();
        queryString.setText(subject.getOQLQuery());

        AbstractEditorPane pane = QueryExecution.createPane(subject, this.getClass());
        createResultPane(pane, queryResult);
    }

    // //////////////////////////////////////////////////////////////
    // job to execute query
    // //////////////////////////////////////////////////////////////

    class OQLJob extends AbstractPaneJob
    {
        String queryString;

        public OQLJob(AbstractEditorPane pane, String queryString)
        {
            super(queryString.toString(), pane);
            this.queryString = queryString;

            this.setUser(true);
        }

        @Override
        protected IStatus doRun(IProgressMonitor monitor)
        {
            try
            {
                QueryDescriptor descriptor = QueryRegistry.instance().getQuery(OQLQuery.class);
                ArgumentContextProvider argumentContextProvider = new ArgumentContextProvider(
                                (HeapEditor) ((MultiPaneEditorSite) getEditorSite()).getMultiPageEditor());

                ArgumentSet argumentSet = descriptor.createNewArgumentSet(argumentContextProvider);

                ArgumentDescriptor a = descriptor.getArgumentByName("queryString");
                argumentSet.setArgumentValue(a, queryString);

                final QueryResult result = argumentSet.execute(new ProgressMonitorWrapper(monitor));

                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        initQueryResult(result);
                    }
                });
            }
            catch (final Exception e)
            {
                PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            createExceptionPane(e, queryString);
                        }
                        catch (PartInitException pie)
                        {
                            ErrorHelper.logThrowable(pie);
                        }
                    }
                });
            }

            return Status.OK_STATUS;
        }
    }

    public void createExceptionPane(Exception e, String queryString) throws PartInitException
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append("Executed Query:\n");
        buf.append(queryString);

        Throwable t = null;
        if (e instanceof SnapshotException)
        {
            buf.append("\n\nProblem reported:\n");
            buf.append(e.getMessage());
            t = e.getCause();
        }
        else
        {
            t = e;
        }

        if (t != null)
        {
            buf.append("\n\n");
            StringWriter w = new StringWriter();
            PrintWriter o = new PrintWriter(w);
            t.printStackTrace(o);
            o.flush();

            buf.append(w.toString());
        }

        createResultPane("TextViewPane", buf.toString());
    }

    // //////////////////////////////////////////////////////////////
    // methods
    // //////////////////////////////////////////////////////////////

    public String getTitle()
    {
        return "OQL";
    }

    @Override
    public Image getTitleImage()
    {
        return MemoryAnalyserPlugin.getImage(MemoryAnalyserPlugin.ISharedImages.OQL);
    }
}
