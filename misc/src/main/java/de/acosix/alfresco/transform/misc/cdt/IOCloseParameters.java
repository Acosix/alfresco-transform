/*
 * Copyright 2021 Acosix GmbH
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
 * Encapsulates the parameters of a {@code IO.close} command.
 *
 * @author Axel Faust
 */
public class IOCloseParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private String handle;

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
        return "close";
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
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        Objects.requireNonNull(this.handle, "this.handle must have been set");

        jsonGenerator.writeStringField("handle", this.handle);
    }

}
