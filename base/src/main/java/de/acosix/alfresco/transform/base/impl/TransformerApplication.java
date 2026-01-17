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
package de.acosix.alfresco.transform.base.impl;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.transform.base.Context;
import de.acosix.alfresco.transform.base.Registry;
import de.acosix.alfresco.transform.base.SharedFileAccessor;
import de.acosix.alfresco.transform.base.TransformationLog;
import de.acosix.alfresco.transform.base.handler.ConfigHandler;
import de.acosix.alfresco.transform.base.handler.HandlerListWithErrorHandling;
import de.acosix.alfresco.transform.base.handler.LogHandler;
import de.acosix.alfresco.transform.base.handler.ProbeHandler;
import de.acosix.alfresco.transform.base.handler.TestFormHandler;
import de.acosix.alfresco.transform.base.handler.TransformHandler;
import de.acosix.alfresco.transform.base.handler.VersionHandler;

/**
 * @author Axel Faust
 */
public class TransformerApplication
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerApplication.class);

    /**
     * Runs this application
     *
     * @param args
     *            command line parameters
     */
    public static void main(final String[] args)
    {
        final TransformerApplication application = new TransformerApplication();
        application.run();
    }

    protected final Context context;

    protected final Registry registry;

    protected final TransformationLog transformationLog;

    protected final SharedFileAccessor sharedFileAccessor;

    protected final VersionHandler versionHandler;

    public TransformerApplication()
    {
        this.context = this.createContext();
        this.registry = new RegistryImpl(this.context);
        this.transformationLog = this.createTransformationLog();
        this.sharedFileAccessor = this.createSharedFileAccessor();

        final String defaultVersion = this.context.getStringProperty("application.version", "N/A");
        this.versionHandler = new VersionHandler(defaultVersion);
    }

    public void run()
    {
        final Server server = this.setupServer();
        this.setupTransformers();

        try
        {
            server.start();
            LOGGER.info("Transformer application started");

            server.join();
            // we have no shutdown handler
            // server only ever stops when process is killed
            // so no point/need in log output after join()
        }
        catch (final Exception ex)
        {
            LOGGER.error("Error running transformer application", ex);
            System.exit(1);
        }
    }

    protected Context createContext()
    {
        return new ContextImpl();
    }

    protected TransformationLog createTransformationLog()
    {
        return new LocalTransformationLog(this.context);
    }

    protected SharedFileAccessor createSharedFileAccessor()
    {
        SharedFileAccessor accessor;
        final String sfsUrl = this.context.getStringProperty(RemoteSharedFileAccessorImpl.SFS_URL);
        if (sfsUrl != null && !sfsUrl.isBlank())
        {
            accessor = new RemoteSharedFileAccessorImpl(this.context);
        }
        else
        {
            accessor = new LocalSharedFileAccessorImpl(this.context);
        }
        return accessor;
    }

    protected Server setupServer()
    {
        final int minThreads = this.context.getIntegerProperty("application.minThreads", 5, 1, Integer.MAX_VALUE);
        final int maxThreads = this.context.getIntegerProperty("application.maxThreads", 200, minThreads, Integer.MAX_VALUE);

        final ThreadPoolExecutor exec = new ThreadPoolExecutor(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
        final ThreadPool pool = new ExecutorThreadPool(exec);

        final Server server = new Server(pool);
        server.setStopAtShutdown(true);
        server.setDumpAfterStart(false);
        server.setDumpBeforeStop(false);

        final HttpConfiguration httpConfig = new HttpConfiguration();
        final HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);

        final ServerConnector connector;
        final SslContextFactory.Server sslContextFactory = this.context.getSslContextFactoryIfEnabled("application.ssl",
                SslContextFactory.Server::new);
        if (sslContextFactory != null)
        {
            if (sslContextFactory.getKeyStorePath() == null)
            {
                throw new IllegalStateException("Path to keystore must be configured for TLS support");
            }
            LOGGER.info("Setting up server in HTTPS-only mode");

            httpConfig.addCustomizer(new SecureRequestCustomizer());
            final SslConnectionFactory tls = new SslConnectionFactory(sslContextFactory, http11.getProtocol());
            connector = new ServerConnector(server, tls, http11);
        }
        else
        {
            LOGGER.info("Setting up server in HTTP-only mode");
            connector = new ServerConnector(server, http11);
        }

        final int port = this.context.getIntegerProperty("application.port", sslContextFactory != null ? 8443 : 8080, 1, 65535);
        connector.setPort(port);

        final String bindHost = this.context.getStringProperty("application.bindHost");
        if (bindHost != null && !bindHost.isBlank())
        {
            connector.setHost(bindHost.trim());
        }

        server.addConnector(connector);

        final HandlerList handlerList = this.createEndpoints();

        final DefaultHandler defaultHandler = new DefaultHandler();
        defaultHandler.setServeIcon(false);
        defaultHandler.setShowContexts(false);
        handlerList.addHandler(defaultHandler);
        server.setHandler(handlerList);

        final String requestLogFilePath = this.context.getStringProperty("application.requestLog.path", "request.log").trim();
        final int requestLogRetainDays = this.context.getIntegerProperty("application.requestLog.retainDays", 7, 0, Integer.MAX_VALUE);
        final String requestLogFormat = this.context.getStringProperty("application.requestLog.format",
                "%{yyyy-MM-dd'T'HH:mm:ssZZZ}t %X \"%r\" %I %s %{ms}T %O");
        final RequestLogWriter requestLogWriter = new RequestLogWriter(requestLogFilePath);
        requestLogWriter.setRetainDays(requestLogRetainDays);
        server.setRequestLog(new CustomRequestLog(requestLogWriter, requestLogFormat));

        return server;
    }

    protected HandlerList createEndpoints()
    {
        final HandlerList handlerList = new HandlerListWithErrorHandling(this.transformationLog);

        handlerList.addHandler(this.versionHandler);
        handlerList.addHandler(new ConfigHandler(this.registry));

        final String applicationName = this.context.getStringProperty("application.name");
        handlerList.addHandler(new TestFormHandler(applicationName, this.registry));
        handlerList.addHandler(new ProbeHandler(this.context, this.registry, this.transformationLog));
        handlerList.addHandler(new TransformHandler(this.context, this.registry, this.transformationLog, this.sharedFileAccessor));
        handlerList.addHandler(new LogHandler(applicationName, this.transformationLog));

        return handlerList;
    }

    protected void setupTransformers()
    {
        // NO-OP - to be extended by more specific transformer applications
    }
}
