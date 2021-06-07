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
package de.acosix.alfresco.transform.misc;

import de.acosix.alfresco.transform.base.impl.TransformerApplication;
import de.acosix.alfresco.transform.misc.cdt.DevToolsWebSocketClient;
import de.acosix.alfresco.transform.misc.cdt.DevToolsWebSocketClientImpl;

/**
 *
 * @author Axel Faust
 */
public class MiscTransformerApplication extends TransformerApplication
{

    /**
     * Runs this application
     *
     * @param args
     *     command line parameters
     */
    public static void main(final String[] args)
    {
        final MiscTransformerApplication application = new MiscTransformerApplication();
        application.run();
    }

    private final DevToolsWebSocketClient client;

    public MiscTransformerApplication()
    {
        super();

        this.client = this.createDevToolsClient();
    }

    protected DevToolsWebSocketClient createDevToolsClient()
    {
        final String devToolsHost = this.context.getStringProperty("devtools.host", "localhost");
        final int devToolsPort = this.context.getIntegerProperty("devtools.port", 9022, 1024, 65535);
        final int devToolsConnectTimeout = this.context.getIntegerProperty("devtools.connectTimeout", 5000, 0, Integer.MAX_VALUE);
        final int devToolsConnectionLostTimeout = this.context.getIntegerProperty("devtools.connectLostTimeout", 15, 0, Integer.MAX_VALUE);
        return DevToolsWebSocketClientImpl.connect(devToolsHost, devToolsPort, devToolsConnectTimeout, devToolsConnectionLostTimeout);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void setupTransformers()
    {
        this.registry.registerTransformer(new DevToolsTransformer(this.context, this.transformationLog, this.client));
        this.registry.registerTransformer(new MailHtmlTransformer(this.context, this.transformationLog));
    }
}
