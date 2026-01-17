/*
 * Copyright 2021 - 2026 Acosix GmbH
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

import java.util.Locale;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Transformer;
import de.acosix.alfresco.transform.base.impl.TransformerApplication;

/**
 *
 * @author Axel Faust
 */
public class OnlyOfficeTransformerApplication extends TransformerApplication
{

    private static final Logger LOGGER = LoggerFactory.getLogger(OnlyOfficeTransformerApplication.class);

    /**
     * Runs this application
     *
     * @param args
     *            command line parameters
     */
    public static void main(final String[] args)
    {
        final OnlyOfficeTransformerApplication application = new OnlyOfficeTransformerApplication();
        application.run();
    }

    private final HttpClient onlyOfficeClient;

    private final TokenManager tokenManager;

    public OnlyOfficeTransformerApplication()
    {
        super();

        final String publicHost = this.context.getStringProperty("onlyoffice.callback.publicHost");
        if (publicHost == null || publicHost.isBlank())
        {
            throw new IllegalStateException("A public host / DNS name via which OnlyOffice can issue callbacks must be configured");
        }

        this.onlyOfficeClient = this.createOnlyOfficeClient();

        final String jwtSecret = this.context.getStringProperty("onlyoffice.jwtSecret");
        if (jwtSecret != null && !jwtSecret.isBlank())
        {
            this.tokenManager = new TokenManager(jwtSecret);
        }
        else
        {
            this.tokenManager = null;
        }
    }

    protected HttpClient createOnlyOfficeClient()
    {
        final String onlyOfficeConversionUrl = this.context.getStringProperty("onlyoffice.conversionUrl");
        if (onlyOfficeConversionUrl == null || onlyOfficeConversionUrl.isBlank())
        {
            throw new IllegalStateException("A Conversion API URL for OnlyOffice must be configured");
        }

        HttpClient onlyOfficeClient;
        if (onlyOfficeConversionUrl.toLowerCase(Locale.ENGLISH).startsWith("https://"))
        {
            LOGGER.info("Starting OnlyOffice client with SSL support");
            final Client sslContextFactory = this.context.getSslContextFactory("onlyoffice.ssl", SslContextFactory.Client::new);
            final ClientConnector clientConnector = new ClientConnector();
            clientConnector.setSslContextFactory(sslContextFactory);
            onlyOfficeClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        }
        else
        {
            LOGGER.info("Starting OnlyOffice client without SSL support");
            onlyOfficeClient = new HttpClient();
        }
        onlyOfficeClient.setMaxRedirects(1);

        try
        {
            onlyOfficeClient.start();
        }
        catch (final Exception e)
        {
            throw new IllegalStateException("Failed to start client for Shared File Store", e);
        }

        return onlyOfficeClient;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected HandlerList createEndpoints()
    {
        final HandlerList handlerList = super.createEndpoints();

        final SourceFileAccessHandler sourceFileAccessHandler = new SourceFileAccessHandler(this.context, this.sharedFileAccessor);
        sourceFileAccessHandler.setTokenManager(this.tokenManager);
        handlerList.addHandler(sourceFileAccessHandler);

        return handlerList;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void setupTransformers()
    {
        this.registry.registerTransformer(this.createTransformer("OnlyOfficeDocument"));
        this.registry.registerTransformer(this.createTransformer("OnlyOfficeSpreadsheet"));
        this.registry.registerTransformer(this.createTransformer("OnlyOfficePresentation"));

        LOGGER.info("Registered OnlyOffice transformers for document, spreadsheet and presentation file types");
    }

    private Transformer createTransformer(final String name)
    {
        final OnlyOfficeTransformer transformer = new OnlyOfficeTransformer(name, this.context, this.transformationLog,
                this.sharedFileAccessor, this.onlyOfficeClient);
        transformer.setTokenManager(this.tokenManager);
        return transformer;
    }
}
