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
 * Encapsulates the response payload of a {@code IO.read} command.
 *
 * @author Axel Faust
 */
public class IOReadResponse implements JsonDeserializableResponsePayload
{

    private String data;

    private Boolean base64Encoded;

    private Boolean eof;

    /**
     * @return the data
     */
    public String getData()
    {
        return this.data;
    }

    /**
     * @return the base64Encoded
     */
    public Boolean getBase64Encoded()
    {
        return this.base64Encoded;
    }

    /**
     * @return the eof
     */
    public Boolean getEof()
    {
        return this.eof;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialise(final JsonNode payload)
    {
        if (payload.hasNonNull("data"))
        {
            this.data = payload.get("data").asText();
        }
        else
        {
            throw new DevToolsException("IO.read response payload does not contain data");
        }
        this.base64Encoded = payload.hasNonNull("base64Encoded") ? payload.get("base64Encoded").asBoolean() : Boolean.FALSE;
        this.eof = payload.hasNonNull("eof") ? payload.get("eof").asBoolean() : Boolean.FALSE;
    }

}
