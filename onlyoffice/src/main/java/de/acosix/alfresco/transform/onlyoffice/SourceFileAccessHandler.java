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
package de.acosix.alfresco.transform.onlyoffice;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.SharedFileAccessException;
import de.acosix.alfresco.transform.base.SharedFileAccessor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 *
 * @author Axel Faust
 */
public class SourceFileAccessHandler extends AbstractHandler
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFileAccessHandler.class);

    public static final String DEFAULT_CONTEXT = "onlyOfficeCallback";

    public static final String TARGET_PREFIX = "/" + DEFAULT_CONTEXT + "/";

    private final Context context;

    private final SharedFileAccessor sharedFileAccessor;

    private final String tokenHeaderName;

    private TokenManager tokenManager;

    public SourceFileAccessHandler(final Context context, final SharedFileAccessor sharedFileAccessor)
    {
        this.context = context;
        this.sharedFileAccessor = sharedFileAccessor;

        this.tokenHeaderName = context.getStringProperty("onlyoffice.tokenHeaderName");
    }

    /**
     * @param tokenManager
     *            the tokenManager to set
     */
    public void setTokenManager(final TokenManager tokenManager)
    {
        this.tokenManager = tokenManager;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void handle(final String target, final Request baseRequest, final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (target.startsWith(TARGET_PREFIX))
        {
            if (baseRequest.getMethod().equals(HttpMethod.GET.name()))
            {
                final String reference = target.substring(TARGET_PREFIX.length());
                if (!reference.isBlank())
                {
                    if (!this.validateAuthorization(request))
                    {
                        response.setStatus(HttpStatus.UNAUTHORIZED_401);
                        response.flushBuffer();
                    }
                    else
                    {
                        this.lookupAndStreamFile(response, reference);
                    }
                }
                else
                {
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    response.flushBuffer();
                }
            }
            else
            {
                response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                response.flushBuffer();
            }
            baseRequest.setHandled(true);
        }
    }

    private boolean validateAuthorization(final HttpServletRequest request)
    {
        boolean validAuthorization = true;
        final String headerName = this.tokenHeaderName != null && !this.tokenHeaderName.isBlank() ? this.tokenHeaderName
                : HttpHeader.AUTHORIZATION.asString();
        final String token = request.getHeader(headerName);
        if (token != null)
        {
            if (this.tokenManager != null)
            {
                validAuthorization = this.tokenManager.isValidToken(token);
            }
            else
            {
                LOGGER.warn("OnlyOffice sent authorization token but transformer was not configured to use / validate tokens");
            }
        }
        else if (this.tokenManager != null)
        {
            LOGGER.warn("OnlyOffice did not send authorization token but transformer was configured to use / validate tokens");
            validAuthorization = false;
        }
        return validAuthorization;
    }

    private void lookupAndStreamFile(final HttpServletResponse response, final String reference) throws IOException
    {
        LOGGER.debug("Processing OnlyOffce transformation source file retrieval for file reference {}", reference);

        final Path requestedSourceFile = this.sharedFileAccessor.retrieveAsTemporyFile(reference,
                contentType -> response.addHeader(HttpHeader.CONTENT_TYPE.asString(), contentType));
        try
        {
            final long size = Files.size(requestedSourceFile);
            response.addHeader(HttpHeader.CONTENT_LENGTH.asString(), String.valueOf(size));

            try (final OutputStream os = response.getOutputStream())
            {
                Files.copy(requestedSourceFile, os);
            }

            LOGGER.debug("Completed streaming {} bytes of {} from reference file {}", size,
                    response.getHeader(HttpHeader.CONTENT_TYPE.asString()), reference);
        }
        finally
        {
            this.context.discardTempFile(requestedSourceFile);

            try
            {
                // single use only
                this.sharedFileAccessor.deleteFile(reference);
            }
            catch (final SharedFileAccessException e)
            {
                if (e.getStatus() != HttpStatus.NOT_FOUND_404)
                {
                    throw e;
                }
                // else safe to ignore - the end result is sufficient
            }
        }
    }

}
