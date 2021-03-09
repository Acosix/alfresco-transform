/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2019 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer.model;

// file copied from alfresco-transform-core, where it was in turn copied from alfresco-shared-file-store, which does not have any publicly released JARs that could be depended upon
// Note: some automatic code changes were applied as part of the copy (e.g. final modifiers)
/**
 * TODO: Copied from org.alfresco.store.entity (alfresco-shared-file-store). To be discussed
 *
 * POJO that describes the ContentRefEntry response, contains {@link FileRefEntity} according to API spec
 */
public class FileRefResponse
{

    private FileRefEntity entry;

    public FileRefResponse()
    {
        // NO-OP
    }

    public FileRefResponse(final FileRefEntity entry)
    {
        this.entry = entry;
    }

    public FileRefEntity getEntry()
    {
        return this.entry;
    }

    public void setEntry(final FileRefEntity entry)
    {
        this.entry = entry;
    }
}
