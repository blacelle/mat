/*******************************************************************************
 * Copyright (c) 2008 Chris Grindstaff.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Chris Grindstaff - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Name("Primitive Arrays With a Constant Value")
@Category("Java Collections")
@Help("List primitive arrays with a constant value.")
public class PrimitiveArraysWithAConstantValueQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    @Help("The array objects. Only primitive arrays will be examined.")
    public IHeapObjectArgument objects;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask("Searching array values...");

        ArrayInt result = new ArrayInt();

        for (int[] objectIds : objects)
        {
            for (int objectId : objectIds)
            {
                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

                if (!snapshot.isArray(objectId))
                    continue;

                IObject object = snapshot.getObject(objectId);
                if (object instanceof IObjectArray)
                    continue;

                IPrimitiveArray array = (IPrimitiveArray) object;

                int length = array.getLength();
                if (length > 1)
                {
                    boolean allSame = true;
                    Object value0 = array.getValueAt(0);
                    for (int i = 1; i < length; i++)
                    {
                        Object valueAt = array.getValueAt(i);
                        if (valueAt.equals(value0))
                            continue;
                        else
                        {
                            allSame = false;
                            break;
                        }
                    }
                    if (allSame)
                        result.add(objectId);
                }
            }
        }

        return new ObjectListResult.Inbound(snapshot, result.toArray());
    }

}