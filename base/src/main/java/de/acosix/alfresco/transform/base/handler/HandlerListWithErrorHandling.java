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
package de.acosix.alfresco.transform.base.handler;

import java.io.IOException;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.StatusException;
import de.acosix.alfresco.transform.base.TransformationLog;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Axel Faust
 */
public class HandlerListWithErrorHandling extends HandlerList
{

    private static final Logger LOGGER = LoggerFactory.getLogger(HandlerListWithErrorHandling.class);

    private final TransformationLog transformationLog;

    public HandlerListWithErrorHandling(final TransformationLog transformationLog, final Handler... handlers)
    {
        super(handlers);
        this.transformationLog = transformationLog;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        try
        {
            super.handle(target, baseRequest, request, response);
        }
        catch (final StatusException e)
        {
            final String message = e.getMessage();
            LOGGER.debug("Reporting error status {} with message {}", e.getStatus(), message);
            if (message == null || message.isBlank())
            {
                response.sendError(e.getStatus());
            }
            else
            {
                response.sendError(e.getStatus(), message);
            }

            this.transformationLog.getCurrentEntry().ifPresent(le -> le.setStatus(e.getStatus(), e.getMessage()));
        }
        catch (final Exception e)
        {
            if (!response.isCommitted())
            {
                LOGGER.error("Uncaught exception in unhandled / incompetely handled request {}", baseRequest, e);
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
            }
            else
            {
                LOGGER.error("Uncaught exception in already handled request {}", baseRequest, e);
            }

            this.transformationLog.getCurrentEntry().ifPresent(le -> {
                if (le.getStatusCode() == -1)
                {
                    le.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
                }
            });
        }
        finally
        {
            this.transformationLog.getCurrentEntry().ifPresent(e -> this.transformationLog.closeCurrentEntry());
        }
    }
}
