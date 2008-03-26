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
package org.eclipse.mat.ui.internal.query.browser;

import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.impl.query.QueryDescriptor;
import org.eclipse.mat.impl.query.QueryRegistry;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.ui.QueryExecution;
import org.eclipse.mat.ui.editor.HeapEditor;
import org.eclipse.mat.ui.util.ImageHelper;


public class QueryHistoryProvider extends QueryBrowserProvider
{

    @Override
    public QueryBrowserPopup.Element[] getElements()
    {
        List<String> history = QueryHistory.getHistoryEntries();
        QueryBrowserPopup.Element[] elements = new QueryBrowserPopup.Element[history.size()];

        int index = 0;
        for (String entry : history)
            elements[index++] = new HQQElement(entry);

        return elements;
    }

    @Override
    public String getName()
    {
        return "History";
    }

    /** history is ordered by typing */
    @Override
    public QueryBrowserPopup.Element[] getElementsSorted()
    {
        if (sortedElements == null)
            sortedElements = getElements();

        return sortedElements;
    }

    static class HQQElement implements QueryBrowserPopup.Element
    {
        QueryDescriptor query;
        String commandLine;

        public HQQElement(String commandLine)
        {
            this.commandLine = commandLine;

            int p = commandLine.indexOf(' ');
            String name = p < 0 ? commandLine : commandLine.substring(0, p);
            query = QueryRegistry.instance().getQuery(name);
        }

        public String getLabel()
        {
            return commandLine;
        }

        public String getUsage()
        {
            return commandLine;
        }

        public QueryDescriptor getQuery()
        {
            return query;
        }

        public void execute(HeapEditor editor) throws SnapshotException
        {
            QueryExecution.execute(editor, commandLine);
        }

        public ImageDescriptor getImageDescriptor()
        {
            if (query != null && query.getIcon() != null)
                return ImageHelper.getImageDescriptor(query);
            return null;
        }

    }

}
