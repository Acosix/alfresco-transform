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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class DevToolsWebSocketClientImpl extends WebSocketClient implements DevToolsWebSocketClient
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DevToolsWebSocketClientImpl.class);

    private final Map<CommandKey, CommandResponseSync> commandSyncs = new HashMap<>();

    private final AtomicInteger idSequence = new AtomicInteger(0);

    private final Map<String, AtomicInteger> idSequenceBySession = new HashMap<>();

    private final List<EventListener> globalListeners = new LinkedList<>();

    private final Map<String, List<EventListener>> sessionListeners = new HashMap<>();

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final String host;

    private final int port;

    private volatile boolean connected;

    private DevToolsWebSocketClientImpl(final URI serverUri, final String host, final int port, final int connectTimeout)
    {
        // TODO configurable connect / connection lost timeouts
        super(serverUri, new Draft_6455(new PerMessageDeflateExtension()), Collections.emptyMap(), connectTimeout);
        this.setConnectionLostTimeout(30);
        this.host = host;
        this.port = port;

        // have to fake Host header because of https://chromium-review.googlesource.com/c/chromium/src/+/952522/
        // sad that Chromium team relies on broken clients / invalid requests as a "sort-of security" measure
        this.addHeader(HttpHeader.HOST.asString(), "localhost:" + port);
    }

    /**
     * Creates and connects a new web socket client instance against a DevTools debugger running on the specified host and port.
     *
     * @param host
     *     the host on which the DevTools debugger is running
     * @param port
     *     the port on which the DevTools debugger is running
     * @param connectTimeout
     *     the connection timeout in milliseconds
     * @param connectionLostTimeout
     *     the interval for checking for lost connection in seconds
     * @return the connected web socket client
     */
    public static DevToolsWebSocketClient connect(final String host, final int port, final int connectTimeout,
            final int connectionLostTimeout)
    {
        final String url = findDevToolsDebuggerWebSocketUrl(host, port);
        final DevToolsWebSocketClientImpl client = new DevToolsWebSocketClientImpl(URI.create(url), host, port, connectTimeout);
        client.setConnectionLostTimeout(connectTimeout);

        try
        {
            if (!client.connectBlocking(connectTimeout, TimeUnit.MILLISECONDS))
            {
                throw new IllegalStateException("Timed out waiting for DevTools web socket connection");
            }
            LOGGER.info("Conected to DevTools debugger via {}", url);
        }
        catch (final InterruptedException e)
        {
            throw new IllegalStateException("Failed to connect to DevTools", e);
        }
        return client;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void send(final String domain, final String command)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");

        this.sendBlockingImpl(domain, command, null, null);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void send(final String domain, final String command, final String sessionId)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(sessionId, "The session ID must be specified");

        this.sendBlockingImpl(domain, command, sessionId, null);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <T extends JsonDeserializableResponsePayload> T send(final String domain, final String command,
            final Supplier<T> responsePayloadFactory)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(responsePayloadFactory, "The response payload factory must be specified");

        return this.sendBlockingImpl(domain, command, null, null, responsePayloadFactory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <T extends JsonDeserializableResponsePayload> T send(final String domain, final String command, final String sessionId,
            final Supplier<T> responsePayloadFactory)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(sessionId, "The session ID must be specified");
        Objects.requireNonNull(responsePayloadFactory, "The response payload factory must be specified");

        return this.sendBlockingImpl(domain, command, sessionId, null, responsePayloadFactory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <R extends JsonSerializableRequestPayload, T extends JsonDeserializableResponsePayload> T send(final String domain,
            final String command, final R requestPayload, final Supplier<T> responsePayloadFactory)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(requestPayload, "The request payload must be specified");
        Objects.requireNonNull(responsePayloadFactory, "The response payload factory must be specified");

        return this.sendBlockingImpl(domain, command, null, requestPayload::serialise, responsePayloadFactory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <R extends JsonSerializableRequestPayload, T extends JsonDeserializableResponsePayload> T send(final String domain,
            final String command, final String sessionId, final R requestPayload, final Supplier<T> responsePayloadFactory)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(sessionId, "The session ID must be specified");
        Objects.requireNonNull(requestPayload, "The request payload must be specified");
        Objects.requireNonNull(responsePayloadFactory, "The response payload factory must be specified");

        return this.sendBlockingImpl(domain, command, sessionId, requestPayload::serialise, responsePayloadFactory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <R extends JsonSerializableRequestPayload> void send(final String domain, final String command, final R requestPayload)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(requestPayload, "The request payload must be specified");

        this.sendBlockingImpl(domain, command, null, requestPayload::serialise);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <R extends JsonSerializableRequestPayload> void send(final String domain, final String command, final String sessionId,
            final R requestPayload)
    {
        Objects.requireNonNull(domain, "The domain of the command must be specified");
        Objects.requireNonNull(command, "The command must be specified");
        Objects.requireNonNull(sessionId, "The session ID must be specified");
        Objects.requireNonNull(requestPayload, "The request payload must be specified");

        this.sendBlockingImpl(domain, command, sessionId, requestPayload::serialise);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerListener(final EventListener listener)
    {
        Objects.requireNonNull(listener, "The listener must be specified");
        this.globalListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerListener(final String sessionId, final EventListener listener)
    {
        Objects.requireNonNull(sessionId, "The session ID must be specified");
        Objects.requireNonNull(listener, "The listener must be specified");

        this.sessionListeners.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected()
    {
        return this.connected;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void reconnect()
    {
        // remote DevTools instance may have been recreated, resulting in a different debugger URL
        final String url = findDevToolsDebuggerWebSocketUrl(this.host, this.port);
        this.uri = URI.create(url);
        try
        {
            super.reconnectBlocking();
        }
        catch (final InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            throw new DevToolsException("Interrupted waiting for reconnect", iex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown()
    {
        try
        {
            super.closeBlocking();
        }
        catch (final InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            throw new DevToolsException("Interrupted waiting for shutdown", iex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discardSessionData(final String sessionId)
    {
        Objects.requireNonNull(sessionId, "The session ID must be specified");

        this.idSequenceBySession.remove(sessionId);
        this.sessionListeners.remove(sessionId);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onOpen(final ServerHandshake handshakedata)
    {
        this.connected = true;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onMessage(final String message)
    {
        LOGGER.debug("Handling web socket message {}", message);
        try
        {
            final JsonNode messageRoot = this.jsonMapper.readTree(message);
            if (messageRoot.isObject())
            {
                if (messageRoot.hasNonNull("id"))
                {
                    this.onCommandResponse(message, messageRoot);
                }
                else if (messageRoot.hasNonNull("method"))
                {
                    this.onEventMessage(messageRoot);
                }
                else
                {
                    LOGGER.debug("Web socket message does not contain a command ID nor a event method", message);
                }
            }
            else
            {
                LOGGER.warn("Web socket message {} is not a JSON object", message);
            }
        }
        catch (final IOException ioex)
        {
            LOGGER.error("Failed to deserialise a web socket message", ioex);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onClose(final int code, final String reason, final boolean remote)
    {
        LOGGER.info(remote ? "Web socket client closed remotely with code {} and reason: {}"
                : "Web socket client closed with code {} and reason: {}", code, reason);
        this.connected = false;

        final List<CommandResponseSync> commandSyncs = new ArrayList<>(this.commandSyncs.values());
        this.commandSyncs.clear();
        commandSyncs
                .forEach(c -> c.complete(new DevToolsException(remote ? "Web socket client closed by peer" : "Web socket client closed")));
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void onError(final Exception ex)
    {
        LOGGER.error("Web socket client error", ex);
    }

    private void onCommandResponse(final String message, final JsonNode messageRoot)
    {
        LOGGER.debug("Handling web socket message as command response");
        final int id = messageRoot.get("id").asInt();
        final String sessionId = messageRoot.hasNonNull("sessionId") ? messageRoot.get("sessionId").asText() : null;
        final CommandResponseSync commandSync = this.commandSyncs.remove(new CommandKey(id, sessionId));
        if (commandSync != null)
        {
            if (messageRoot.hasNonNull("result"))
            {
                commandSync.complete(messageRoot.get("result"));
            }
            else if (messageRoot.hasNonNull("error"))
            {
                final JsonNode errorRoot = messageRoot.get("error");
                final String errorMessage = errorRoot.get("message").asText();
                final String errorDetail = errorRoot.has("data") ? errorRoot.get("data").asText() : null;

                DevToolsException ex;
                if (errorDetail != null)
                {
                    ex = new DevToolsException(errorMessage + " - " + errorDetail);
                }
                else
                {
                    ex = new DevToolsException(errorMessage);
                }
                commandSync.complete(ex);
            }
            else
            {
                commandSync.complete();
            }
        }
        else
        {
            LOGGER.warn("Web socket message {} cannot be linked to a known command invocation", message);
        }
    }

    private void onEventMessage(final JsonNode messageRoot)
    {
        LOGGER.debug("Handling web socket message as event notification");

        final String method = messageRoot.get("method").asText();
        final int sepIdx = method.indexOf('.');
        final String domain = method.substring(0, sepIdx);
        final String command = method.substring(sepIdx + 1);

        final JsonNode params = messageRoot.get("params");
        final String sessionId = messageRoot.hasNonNull("sessionId") ? messageRoot.get("sessionId").asText() : null;

        final Predicate<? super EventListener> listenerHandler = l -> {
            boolean remove = false;
            try
            {
                remove = !l.eventReceived(domain, command, sessionId, params);
            }
            catch (final Exception e)
            {
                LOGGER.error("Unhandled exception from listener", e);
            }
            return remove;
        };

        final List<EventListener> globalListenersToDiscard = this.globalListeners.stream().filter(listenerHandler)
                .collect(Collectors.toList());
        this.globalListeners.removeAll(globalListenersToDiscard);

        if (this.sessionListeners.containsKey(sessionId))
        {
            final List<EventListener> sessionListeners = this.sessionListeners.get(sessionId);
            final List<EventListener> sessionListenersToDiscard = sessionListeners.stream().filter(listenerHandler)
                    .collect(Collectors.toList());
            sessionListeners.removeAll(sessionListenersToDiscard);
        }
    }

    private void sendBlockingImpl(final String domain, final String command, final String sessionId,
            final CommandParamsFieldsWriter paramsFieldWriter)
    {
        this.sendBlockingImpl(domain, command, sessionId, paramsFieldWriter, n -> {
            // NO-OP
        });
    }

    private <T extends JsonDeserializableResponsePayload> T sendBlockingImpl(final String domain, final String command,
            final String sessionId, final CommandParamsFieldsWriter paramsFieldWriter, final Supplier<T> responsePayloadFactory)
    {
        final AtomicReference<T> response = new AtomicReference<>();

        this.sendBlockingImpl(domain, command, sessionId, paramsFieldWriter, n -> {
            final T t = responsePayloadFactory.get();
            t.deserialise(n);
            response.set(t);
        });

        if (response.get() == null)
        {
            throw new DevToolsException("No command response payload received");
        }

        return response.get();
    }

    private void sendBlockingImpl(final String domain, final String command, final String sessionId,
            final CommandParamsFieldsWriter paramsFieldWriter, final Consumer<JsonNode> responseCallback)
    {
        Objects.requireNonNull(responseCallback, "The response callback must be specified");

        final int id;

        final ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
        try (final JsonGenerator jsonGenerator = this.jsonMapper.createGenerator(bos))
        {
            jsonGenerator.writeStartObject();
            if (sessionId != null)
            {
                jsonGenerator.writeStringField("sessionId", sessionId);
                id = this.idSequenceBySession.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).incrementAndGet();
            }
            else
            {
                id = this.idSequence.incrementAndGet();
            }
            jsonGenerator.writeNumberField("id", id);
            jsonGenerator.writeStringField("method", domain + "." + command);
            if (paramsFieldWriter != null)
            {
                jsonGenerator.writeObjectFieldStart("params");
                paramsFieldWriter.writeCommandParamsFields(jsonGenerator);
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndObject();
        }
        catch (final IOException ioex)
        {
            throw new DevToolsException("Failed to write DevTools web socket command", ioex);
        }

        final CommandKey commandKey = new CommandKey(id, sessionId);
        final CommandResponseSync commandSync = new CommandResponseSync();

        this.commandSyncs.put(commandKey, commandSync);
        try
        {
            this.send(new String(bos.toByteArray()));

            // TODO configurable timeout
            final Optional<JsonNode> response = commandSync.waitForResponse(30000);
            response.ifPresent(responseCallback);
        }
        finally
        {
            this.commandSyncs.remove(commandKey);
        }
    }

    private static String findDevToolsDebuggerWebSocketUrl(final String host, final int port)
    {
        LOGGER.debug("Attempting to determine DevTools debugger web socket URL on host {} and port {}", host, port);
        try
        {
            final HttpClient httpClient = new HttpClient();
            httpClient.setMaxRedirects(0);
            httpClient.start();
            try
            {
                final String devToolsDebugerListUrl = buildDevToolsDebuggerListUrl(host, port);
                final ContentResponse response = httpClient.newRequest(devToolsDebugerListUrl).method(HttpMethod.GET)
                        // have to fake Host header because of https://chromium-review.googlesource.com/c/chromium/src/+/952522/
                        // sad that Chromium team relies on broken clients / invalid requests as a "sort-of security" measure
                        .headers(m -> m.add(HttpHeader.HOST, "localhost:" + port)).send();

                if (response.getStatus() == HttpStatus.OK_200)
                {
                    final JsonMapper jsonMapper = JsonMapper.builder().build();
                    final JsonNode responseRoot = jsonMapper.readTree(response.getContentAsString());
                    if (responseRoot.isArray())
                    {
                        final Iterator<JsonNode> it = responseRoot.elements();
                        while (it.hasNext())
                        {
                            final JsonNode el = it.next();

                            if (el.isObject())
                            {
                                final String type = el.hasNonNull("type") ? el.get("type").asText() : null;
                                final String title = el.hasNonNull("title") ? el.get("title").asText() : null;
                                final String url = el.hasNonNull("url") ? el.get("url").asText() : null;
                                String webSocketDebuggerUrl = el.hasNonNull("webSocketDebuggerUrl")
                                        ? el.get("webSocketDebuggerUrl").asText()
                                        : null;

                                if (webSocketDebuggerUrl != null && ("page".equals(type)
                                        && ("none".equals(title) || "about:blank".equals(title) || "chrome://newtab/".equals(url))))
                                {
                                    // due to host header workaround, we need to replace the wrong host name in found debugger URL
                                    webSocketDebuggerUrl = webSocketDebuggerUrl.replaceFirst("localhost", host);
                                    LOGGER.debug("Located idle / empty page instance with DevTools debugger web socket url {}",
                                            webSocketDebuggerUrl);
                                    return webSocketDebuggerUrl;
                                }
                            }
                        }
                    }
                }
                else
                {
                    LOGGER.warn("Request on {} yielded response status {}", devToolsDebugerListUrl, response.getStatus());
                }

                throw new IllegalStateException("Failed to locate DevTools web socket");
            }
            finally
            {
                httpClient.stop();
            }
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to connect to DevTools", e);
        }
        catch (final Exception e)
        {
            throw new IllegalStateException("Failed to connect to DevTools", e);
        }
    }

    private static String buildDevToolsDebuggerListUrl(final String host, final int port)
    {
        final StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("http://");
        urlBuilder.append(host);
        urlBuilder.append(":");
        urlBuilder.append(port);
        urlBuilder.append("/json/list");
        final String devToolsDebugerListUrl = urlBuilder.toString();
        return devToolsDebugerListUrl;
    }

    /**
     *
     * @author Axel Faust
     */
    @FunctionalInterface
    private interface CommandParamsFieldsWriter
    {

        /**
         * Writes fields of a commend parameter object via the provided JSON generator, which has already initialised the container object.
         *
         * @param jsonGenerator
         *     the generator to use for writing the parameter fields
         * @throws IOException
         *     if an error occurs writing the JSON fields
         */
        void writeCommandParamsFields(JsonGenerator jsonGenerator) throws IOException;
    }

    private static class CommandKey
    {

        private final int id;

        private final String sessionId;

        public CommandKey(final int id, final String sessionId)
        {
            this.id = id;
            this.sessionId = sessionId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + this.id;
            result = prime * result + ((this.sessionId == null) ? 0 : this.sessionId.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final CommandKey other = (CommandKey) obj;
            if (this.id != other.id)
            {
                return false;
            }
            if (this.sessionId == null)
            {
                if (other.sessionId != null)
                {
                    return false;
                }
            }
            else if (!this.sessionId.equals(other.sessionId))
            {
                return false;
            }
            return true;
        }
    }

    private static class CommandResponseSync
    {

        private JsonNode response;

        private Exception exception;

        private final CountDownLatch latch = new CountDownLatch(1);

        protected Optional<JsonNode> waitForResponse(final long timeout)
        {
            try
            {
                if (!this.latch.await(timeout, TimeUnit.MILLISECONDS))
                {
                    throw new DevToolsException("Timed out while waiting for response to web socket command");
                }
            }
            catch (final InterruptedException iex)
            {
                Thread.currentThread().interrupt();
                throw new DevToolsException("Interrupted while waiting for response to web socket command", iex);
            }

            // always rethrow as wrapped exception due to difference in stacktraces
            if (this.exception != null)
            {
                throw new DevToolsException(this.exception instanceof DevToolsException ? this.exception.getMessage()
                        : "Error during handling of web socket command", this.exception);
            }

            return Optional.ofNullable(this.response);
        }

        protected void complete()
        {
            this.latch.countDown();
        }

        protected void complete(final JsonNode response)
        {
            this.response = response;
            this.latch.countDown();
        }

        protected void complete(final Exception exception)
        {
            this.exception = exception;
            this.latch.countDown();
        }
    }
}
