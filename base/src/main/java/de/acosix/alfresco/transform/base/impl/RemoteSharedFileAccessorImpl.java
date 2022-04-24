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
package de.acosix.alfresco.transform.base.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.alfresco.transformer.model.FileRefResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.MultiPartRequestContent;
import org.eclipse.jetty.client.util.PathRequestContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.SharedFileAccessException;
import de.acosix.alfresco.transform.base.SharedFileAccessor;
import de.acosix.alfresco.transform.base.StatusException;

/**
 * @author Axel Faust
 */
public class RemoteSharedFileAccessorImpl implements SharedFileAccessor
{

    public static final String SFS_URL = "sfs.url";

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSharedFileAccessorImpl.class);

    private final Path downloadDir;

    private final String baseUrl;

    private final int responseReadTimeout;

    private final HttpClient httpClient;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RemoteSharedFileAccessorImpl(final Context context)
    {
        this.downloadDir = context.createTempFileSubDirectory("sfsDownloads");
        this.baseUrl = context.getStringProperty(SFS_URL);
        if (this.baseUrl == null || this.baseUrl.isBlank())
        {
            throw new IllegalStateException("The URL for the Shared File Store has not been configured");
        }
        this.responseReadTimeout = context.getIntegerProperty("sfs.responseTimeoutMillis", 5000, 0, 300000);

        if (this.baseUrl.toLowerCase(Locale.ENGLISH).startsWith("https://"))
        {
            LOGGER.info("Starting remote Shared File Store accessor client with SSL support");
            final Client sslContextFactory = context.getSslContextFactory("sfs.ssl", SslContextFactory.Client::new);
            final ClientConnector clientConnector = new ClientConnector();
            clientConnector.setSslContextFactory(sslContextFactory);
            this.httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        }
        else
        {
            LOGGER.info("Starting remote Shared File Store accessor client without SSL support");
            this.httpClient = new HttpClient();
        }
        this.httpClient.setMaxRedirects(1);

        try
        {
            this.httpClient.start();
        }
        catch (final Exception e)
        {
            throw new IllegalStateException("Failed to start client for Shared File Store", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path retrieveAsTemporyFile(final String fileReference, final Consumer<String> contentTypeConsumer)
    {
        LOGGER.debug("Retrieving shared file for reference {}", fileReference);
        final String effectiveUrl = this.baseUrl + "/" + fileReference;
        try
        {
            final InputStreamResponseListener listener = new InputStreamResponseListener();
            this.httpClient.newRequest(effectiveUrl).method(HttpMethod.GET).send(listener);
            final Response response = listener.get(this.responseReadTimeout, TimeUnit.MILLISECONDS);

            if (response.getStatus() == HttpStatus.OK_200)
            {
                return this.processFileResponse(fileReference, contentTypeConsumer, listener, response);
            }
            else
            {
                discardResponse(listener);
                LOGGER.error("Failed to retrieve shared file {} with HTTP status {} - {}", fileReference, response.getStatus(),
                        response.getReason());
                throw new SharedFileAccessException(
                        HttpStatus.NOT_FOUND_404 == response.getStatus() ? HttpStatus.NOT_FOUND_404 : HttpStatus.INTERNAL_SERVER_ERROR_500,
                        "Failed to retrieve file from Shared File Store");
            }
        }
        catch (final StatusException stex)
        {
            throw stex;
        }
        catch (final InterruptedException e)
        {
            LOGGER.error("Thread was interrupted waiting to retrieve shared file {}", fileReference);
            Thread.currentThread().interrupt();
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve file from Shared File Store", e);
        }
        catch (final ExecutionException | TimeoutException | IOException e)
        {
            LOGGER.error("Failed to retrieve shared file {}", fileReference);
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to retrieve file from Shared File Store", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String saveFile(final Path file, final String contentType)
    {
        LOGGER.debug("Storing {} as shared file", file);
        try
        {
            final PathRequestContent fileContent = new PathRequestContent(contentType, file);
            final MultiPartRequestContent requestContent = new MultiPartRequestContent();
            requestContent.addFilePart("file", file.getFileName().toString(), fileContent, HttpFields.EMPTY);
            requestContent.close();

            final ContentResponse response = this.httpClient.newRequest(this.baseUrl).method(HttpMethod.POST).body(requestContent).send();
            final FileRefResponse fileRefResponse = this.jsonMapper.readValue(response.getContentAsString(), FileRefResponse.class);
            final String fileReference = fileRefResponse.getEntry().getFileRef();
            LOGGER.debug("Stored {} as shared file with reference {}", file, fileReference);
            return fileReference;
        }
        catch (final InterruptedException e)
        {
            LOGGER.error("Thread was interrupted waiting to store {} as shared file", file);
            Thread.currentThread().interrupt();
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to store file in Shared File Store", e);
        }
        catch (final ExecutionException | TimeoutException | IOException e)
        {
            LOGGER.error("Failed to store {} as shared file", file);
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to store file in Shared File Store", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(final String fileReference)
    {
        LOGGER.debug("Deleting shared file {}", fileReference);
        final String effectiveUrl = this.baseUrl + "/" + fileReference;
        try
        {
            final ContentResponse response = this.httpClient.newRequest(effectiveUrl).method(HttpMethod.DELETE).send();
            final int status = response.getStatus();
            if (status == HttpStatus.OK_200 || status == HttpStatus.NO_CONTENT_204)
            {
                LOGGER.debug("Deleted shared file {}", fileReference);
            }
            else
            {
                LOGGER.error("Failed to delete shared file {} with HTTP status {} - {}", fileReference, response.getStatus(),
                        response.getReason());
                throw new SharedFileAccessException(
                        HttpStatus.NOT_FOUND_404 == response.getStatus() ? HttpStatus.NOT_FOUND_404 : HttpStatus.INTERNAL_SERVER_ERROR_500,
                        "Failed to delete file in Shared File Store");
            }
        }
        catch (final InterruptedException e)
        {
            LOGGER.error("Thread was interrupted waiting to delete shared file {}", fileReference);
            Thread.currentThread().interrupt();
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to delete file in Shared File Store", e);
        }
        catch (final ExecutionException | TimeoutException e)
        {
            LOGGER.error("Failed to delete shared file {}", fileReference);
            throw new SharedFileAccessException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to delete file in Shared File Store", e);
        }
    }

    private Path processFileResponse(final String fileReference, final Consumer<String> contentTypeConsumer,
            final InputStreamResponseListener listener, final Response response) throws IOException
    {
        final HttpFields headers = response.getHeaders();

        String fileName = fileReference;
        final String disposition = headers.get("Content-Disposition");
        if (disposition != null)
        {
            fileName = Arrays.stream(disposition.split("; *")).filter(s -> s.startsWith("filename=")).findFirst()
                    .map(s -> s.substring("filename=".length())).orElse(fileReference);
        }

        final String contentType = headers.get(HttpHeader.CONTENT_TYPE);
        final long size = headers.getLongField(HttpHeader.CONTENT_LENGTH);

        contentTypeConsumer.accept(contentType);

        final Path downloadFile = Files.createFile(this.downloadDir.resolve(fileName));
        try (final InputStream is = listener.getInputStream())
        {
            Files.copy(is, downloadFile, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ioex)
        {
            final long usableSpace = downloadFile.toFile().getUsableSpace();
            if (usableSpace <= size)
            {
                LOGGER.error("Not enough spasce available to store {} bytes in {}", size, downloadFile);
                throw new SharedFileAccessException(HttpStatus.INSUFFICIENT_STORAGE_507, "Insufficient space to store the shared file",
                        ioex);
            }
            throw ioex;
        }

        LOGGER.debug("Read shared file {} to {} with {} bytes and {} as content type", fileReference, downloadFile, size, contentType);

        return downloadFile;
    }

    private static void discardResponse(final InputStreamResponseListener listener)
    {
        try
        {
            listener.getInputStream().close();
        }
        catch (final IOException ignore)
        {
            // ignore - close input stream primarily as indicator to Jetty client components to discard any further received data
        }
    }
}
