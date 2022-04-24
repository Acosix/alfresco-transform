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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Objects;

/**
 * Encapsulates the parameters of a {@code Target.closeTarget} command.
 *
 * @author Axel Faust
 */
public class CloseTargetParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private String targetId;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain()
    {
        return "Target";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand()
    {
        return "closeTarget";
    }

    /**
     * @param targetId
     *     the targetId to set
     */
    public void setTargetId(final String targetId)
    {
        this.targetId = targetId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        Objects.requireNonNull(this.targetId, "this.targetId must have been set");

        jsonGenerator.writeStringField("targetId", this.targetId);
    }

}
