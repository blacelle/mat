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
package org.eclipse.mat.parser;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.osgi.framework.BundleContext;

public class ParserPlugin extends Plugin
{
    public static final String PLUGIN_ID = "org.eclipse.mat.parser";

    private static ParserPlugin plugin;
    
    private IExtensionTracker tracker;

    public ParserPlugin()
    {}

    public void start(BundleContext context) throws Exception
    {
        super.start(context);
        tracker = new ExtensionTracker(Platform.getExtensionRegistry());
        plugin = this;
    }

    public void stop(BundleContext context) throws Exception
    {
        plugin = null;
        tracker.close();
        super.stop(context);
    }

    public static ParserPlugin getDefault()
    {
        return plugin;
    }

    public IExtensionTracker getExtensionTracker()
    {
        return tracker;
    }

}
