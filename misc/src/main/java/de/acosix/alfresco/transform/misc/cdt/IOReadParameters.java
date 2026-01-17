/*
 * Copyright 2021 - 2026 Acosix GmbH
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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Objects;

/**
 * Encapsulates the parameters of a {@code IO.read} command.
 *
 * @author Axel Faust
 */
public class IOReadParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private String handle;

    private Integer offset;

    private Integer size;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain()
    {
        return "IO";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand()
    {
        return "read";
    }

    /**
     * @param handle
     *     the handle to set
     */
    public void setHandle(final String handle)
    {
        this.handle = handle;
    }

    /**
     * @param offset
     *     the offset to set
     */
    public void setOffset(final Integer offset)
    {
        this.offset = offset;
    }

    /**
     * @param size
     *     the size to set
     */
    public void setSize(final Integer size)
    {
        this.size = size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        Objects.requireNonNull(this.handle, "this.handle must have been set");

        jsonGenerator.writeStringField("handle", this.handle);
        if (this.size != null)
        {
            jsonGenerator.writeNumberField("size", this.size);
        }
        if (this.offset != null)
        {
            jsonGenerator.writeNumberField("offset", this.offset);
        }
    }

}
