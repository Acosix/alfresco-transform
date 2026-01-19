/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * -
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 * -
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * -
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * -
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transform.base.model;

import java.util.Objects;

// file copied from alfresco-transform-core, where it was in turn copied from alfresco-shared-file-store, which does not have any publicly released JARs that could be depended upon
// Note: some automatic code changes were applied as part of the copy (e.g. final modifiers)
/**
 * TODO: Copied from org.alfresco.store.entity (alfresco-shared-file-store). To be discussed
 *
 * POJO that represents content reference ({@link java.util.UUID})
 */
public class FileRefEntity
{
    private String fileRef;

    public FileRefEntity() {}

    public FileRefEntity(final String fileRef)
    {
        this.fileRef = fileRef;
    }

    public void setFileRef(final String fileRef)
    {
        this.fileRef = fileRef;
    }

    public String getFileRef()
    {
        return this.fileRef;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || this.getClass() != o.getClass())
        {
            return false;
        }
        final FileRefEntity that = (FileRefEntity) o;
        return Objects.equals(this.fileRef, that.fileRef);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.fileRef);
    }

    @Override
    public String toString()
    {
        return this.fileRef;
    }
}
