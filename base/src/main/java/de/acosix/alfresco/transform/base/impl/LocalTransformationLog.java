/*
 * Copyright 2021 - 2022 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.transform.base.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.TransformationLog;

/**
 *
 * @author Axel Faust
 */
public class LocalTransformationLog implements TransformationLog
{

    private final String hostName;

    private final int maxEntries;

    private final AtomicInteger sequenceNumber = new AtomicInteger(0);

    private final ThreadLocal<LocalMutableTransformationLogEntry> currentLogEntry = new ThreadLocal<>();

    private final List<Entry> logEntries = new LinkedList<>();

    /**
     * Creates a new instance of this class.
     *
     * @param context
     *            the configuration context of the transformer application
     */
    public LocalTransformationLog(final Context context)
    {
        this.hostName = context.getStringProperty("application.host");
        this.maxEntries = context.getIntegerProperty("localTransformationLog.maxEntries", 100, 1, Integer.MAX_VALUE);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public List<Entry> getMostRecentEntries(final int count)
    {
        List<Entry> entries;
        synchronized (this.logEntries)
        {
            entries = this.logEntries.subList(Math.max(0, this.logEntries.size() - count), this.logEntries.size());
            entries = new ArrayList<>(entries);
        }
        Collections.reverse(entries);
        return entries;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public List<Entry> getMostRecentHostEntries(final int count)
    {
        List<Entry> entries;
        synchronized (this.logEntries)
        {
            entries = this.logEntries.subList(Math.max(0, this.logEntries.size() - count), this.logEntries.size());
            entries = new ArrayList<>(entries);
        }
        Collections.reverse(entries);
        return entries;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public MutableEntry startNewEntry()
    {
        final LocalMutableTransformationLogEntry logEntry = this.currentLogEntry.get();
        if (logEntry != null)
        {
            throw new IllegalStateException("A transformation log entry has already been started in the current thread");
        }

        final LocalMutableTransformationLogEntry newLogEntry = new LocalMutableTransformationLogEntry(this.hostName,
                this.sequenceNumber.incrementAndGet());
        this.currentLogEntry.set(newLogEntry);
        return newLogEntry;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Optional<MutableEntry> getCurrentEntry()
    {
        final LocalMutableTransformationLogEntry logEntry = this.currentLogEntry.get();
        return Optional.ofNullable(logEntry);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void closeCurrentEntry()
    {
        final LocalMutableTransformationLogEntry logEntry = this.currentLogEntry.get();
        if (logEntry == null)
        {
            throw new IllegalStateException("No transformation log entry has been started in the current thread");
        }

        final Entry closedEntry = logEntry.closeEntry();
        synchronized (this.logEntries)
        {
            this.logEntries.add(closedEntry);
            if (this.logEntries.size() > this.maxEntries)
            {
                this.logEntries.subList(0, this.logEntries.size() - this.maxEntries).clear();
            }
        }
        this.currentLogEntry.remove();
    }

}
