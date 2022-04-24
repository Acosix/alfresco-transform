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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Encapsulates the response payload of a {@code Target.attachToTarget} command.
 *
 * @author Axel Faust
 */
public class AttachToTargetResponse implements JsonDeserializableResponsePayload
{

    private String sessionId;

    /**
     * @return the sessionId
     */
    public String getSessionId()
    {
        return this.sessionId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialise(final JsonNode payload)
    {
        if (payload.hasNonNull("sessionId"))
        {
            this.sessionId = payload.get("sessionId").asText();
        }
        else
        {
            throw new DevToolsException("Target.attachToTarget response payload does not contain a session ID");
        }
    }

}
