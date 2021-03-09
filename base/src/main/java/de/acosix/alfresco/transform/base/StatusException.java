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
package de.acosix.alfresco.transform.base;

/**
 * @author Axel Faust
 */
public class StatusException extends RuntimeException
{

    private static final long serialVersionUID = 4925563542158441581L;

    private final int status;

    public StatusException(final int status)
    {
        super();
        this.status = status;
    }

    public StatusException(final int status, final String message)
    {
        super(message);
        this.status = status;
    }

    public StatusException(final int status, final Throwable cause)
    {
        super(cause);
        this.status = status;
    }

    public StatusException(final int status, final String message, final Throwable cause)
    {
        super(message, cause);
        this.status = status;
    }

    /**
     * @return the status
     */
    public int getStatus()
    {
        return this.status;
    }

}
