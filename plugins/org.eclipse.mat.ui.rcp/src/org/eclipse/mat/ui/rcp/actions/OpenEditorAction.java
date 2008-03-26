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
package org.eclipse.mat.ui.rcp.actions;

import java.util.Properties;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.editor.PathEditorInput;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.intro.IIntroSite;
import org.eclipse.ui.intro.config.IIntroAction;


public class OpenEditorAction extends Action implements IIntroAction
{

    public OpenEditorAction()
    {}

    public void run(IIntroSite site, Properties params)
    {

        try
        {
            if (params == null || (!params.containsKey("param"))) { return; }
            Path path = new Path(params.getProperty("param"));

            if (path.toFile().exists())
            {
                IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
                IEditorDescriptor descriptor = registry.getDefaultEditor(params.getProperty("param"));
                IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                                new PathEditorInput(path), descriptor.getId(), true);
                PlatformUI.getWorkbench().getIntroManager().closeIntro(
                                PlatformUI.getWorkbench().getIntroManager().getIntro());
            }

        }
        catch (PartInitException e)
        {
            throw new RuntimeException(e);
        }
        catch (Exception e)
        {
            IStatus status = new Status(IStatus.ERROR, "org.eclipse.mat.ui.rcp", IStatus.OK,
                            "Error opening the heap dump", e);
            MemoryAnalyserPlugin.log(status);
        }

    }

}
