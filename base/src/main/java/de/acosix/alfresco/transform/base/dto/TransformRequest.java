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
package de.acosix.alfresco.transform.base.dto;

/**
 * Slightly enhanced transform request transfer object class to support timeout for consistency with {@code multipart/form-data}
 * transformation requests.
 *
 * @author Axel Faust
 */
public class TransformRequest extends org.alfresco.transform.client.model.TransformRequest
{

    private static final long serialVersionUID = 6539337213249502245L;

    private Long timeout;

    /**
     * @return the timeout
     */
    public Long getTimeout()
    {
        return this.timeout;
    }

    /**
     * @param timeout
     *            the timeout to set
     */
    public void setTimeout(final Long timeout)
    {
        this.timeout = timeout;
    }

}
