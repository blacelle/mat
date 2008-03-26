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
package org.eclipse.mat.parser.internal.snapshot;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.parser.internal.SnapshotImpl;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.parser.model.ClassLoaderImpl;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ClassLoaderHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.HistogramRecord;
import org.eclipse.mat.snapshot.SnapshotException;


public class HistogramBuilder extends HistogramRecord
{
    private static final long serialVersionUID = 2L;

    protected HashMapIntObject<Object> data;

    public HistogramBuilder(String label)
    {
        super(label);
        this.data = new HashMapIntObject<Object>();
    }

    public void put(ClassHistogramRecord record)
    {
        Object previous = data.put(record.getClassId(), record);

        if (previous != null)
            throw new IllegalArgumentException(
                            "Failed to store class data in histogram! Class data for this class id already stored in histogram!");
    }

    public void add(int classId, int objectId, long heapSize)
    {
        ClassHistogramRecordBuilder object = (ClassHistogramRecordBuilder) data.get(classId);
        if (object == null)
            data.put(classId, object = new ClassHistogramRecordBuilder(null, classId));

        object.add(objectId, heapSize);
    }

    public Histogram toHistogram(SnapshotImpl snapshot, boolean isDefaultHistogram) throws SnapshotException
    {
        ArrayList<ClassHistogramRecord> classHistogramRecords = new ArrayList<ClassHistogramRecord>(data.size());
        HashMapIntObject<ClassLoaderHistogramRecordBuilder> cl2builder = new HashMapIntObject<ClassLoaderHistogramRecordBuilder>();

        for (Iterator<?> e = data.values(); e.hasNext();)
        {
            ClassHistogramRecord record;

            Object obj = e.next();

            // convert (if necessary) into proper class histogram record
            if (obj instanceof ClassHistogramRecordBuilder)
                record = ((ClassHistogramRecordBuilder) obj).toClassHistogramRecord();
            else
                record = (ClassHistogramRecord) obj;

            ClassImpl clasz = (ClassImpl) snapshot.getObject(record.getClassId());
            record.setLabel(clasz.getName());

            classHistogramRecords.add(record);

            // add class loader information
            int classLoaderId = clasz.getClassLoaderId();
            ClassLoaderHistogramRecordBuilder clRec = cl2builder.get(classLoaderId);
            if (clRec == null)
            {
                String loaderLabel = snapshot.getClassLoaderLabel(classLoaderId);

                long retainedHeapSize = isDefaultHistogram ? ClassLoaderImpl.doGetRetainedHeapSizeOfObjects(snapshot,
                                classLoaderId, false, false, null) : 0;

                clRec = new ClassLoaderHistogramRecordBuilder(loaderLabel, classLoaderId, retainedHeapSize);
                cl2builder.put(classLoaderId, clRec);
            }
            clRec.add(record);
        }

        int numberOfObjectsOverall = 0;
        long usedHeapSizeOverall = 0;
        long retainedHeapSizeOverall = 0;

        ArrayList<ClassLoaderHistogramRecord> classLoaderHistogramRecords = new ArrayList<ClassLoaderHistogramRecord>(
                        cl2builder.size());

        for (Iterator<?> e = cl2builder.values(); e.hasNext();)
        {
            ClassLoaderHistogramRecordBuilder builder = (ClassLoaderHistogramRecordBuilder) e.next();
            classLoaderHistogramRecords.add(builder.toClassLoaderHistogramRecord(isDefaultHistogram));
            numberOfObjectsOverall += builder.getNumberOfObjects();
            usedHeapSizeOverall += builder.getUsedHeapSize();
            retainedHeapSizeOverall += builder.getRetainedHeapSize();
        }

        return new Histogram(getLabel(), classHistogramRecords, classLoaderHistogramRecords, numberOfObjectsOverall,
                        usedHeapSizeOverall, retainedHeapSizeOverall, isDefaultHistogram);
    }
}
