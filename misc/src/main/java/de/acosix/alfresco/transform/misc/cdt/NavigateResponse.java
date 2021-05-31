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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Encapsulates the response payload of a {@code Page.navigate} command.
 *
 * @author Axel Faust
 */
public class NavigateResponse implements JsonDeserializableResponsePayload
{

    private String frameId;

    private String loaderId;

    private String errorText;

    /**
     * @return the frameId
     */
    public String getFrameId()
    {
        return this.frameId;
    }

    /**
     * @return the loaderId
     */
    public String getLoaderId()
    {
        return this.loaderId;
    }

    /**
     * @return the errorText
     */
    public String getErrorText()
    {
        return this.errorText;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialise(final JsonNode payload)
    {
        if (payload.hasNonNull("frameId"))
        {
            this.frameId = payload.get("frameId").asText();
        }
        else
        {
            throw new DevToolsException("Page.navigate response payload does not contain frame ID");
        }
        this.loaderId = payload.hasNonNull("loaderId") ? payload.get("loaderId").asText() : null;
        this.errorText = payload.hasNonNull("errorText") ? payload.get("errorText").asText() : null;
    }

}
