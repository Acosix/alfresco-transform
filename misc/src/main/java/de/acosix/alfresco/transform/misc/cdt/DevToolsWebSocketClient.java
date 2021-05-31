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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Supplier;

/**
 * Instances of this interface provide simplified, command-only access to a Chrome or Chromium browser via web sockets and the DevTools
 * protocol.
 *
 * Any instance of this interface is not thread-safe with regards to the {@link #isConnected() connected state},
 * {@link #registerListener(EventListener) listeners}, {@link #discardSessionData(String) session state}, and {@link #reconnect() reconnect}
 * / {@link #shutdown() shutdown} operations.
 *
 * @author Axel Faust
 */
public interface DevToolsWebSocketClient
{

    /**
     * Sends an unparameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     */
    void send(String domain, String command);

    /**
     * Sends an unparameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param sessionId
     *     the ID of the session in which to execute the command
     */
    void send(String domain, String command, String sessionId);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <T>
     *     the type of command response payload
     * @return the response payload
     */
    <T extends JsonDeserializableResponsePayload> T send(String domain, String command, Supplier<T> responsePayloadFactory);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param sessionId
     *     the ID of the session in which to execute the command
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <T>
     *     the type of command response payload
     * @return the response payload
     */
    <T extends JsonDeserializableResponsePayload> T send(String domain, String command, String sessionId,
            Supplier<T> responsePayloadFactory);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param requestPayload
     *     the command payload
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <R>
     *     the type of the command payload
     * @param <T>
     *     the type of command response payload
     * @return the command response payload
     */
    default <R extends JsonSerializableRequestPayload & CommandBoundPayload, T extends JsonDeserializableResponsePayload> T send(
            final R requestPayload, final Supplier<T> responsePayloadFactory)
    {
        return this.send(requestPayload.getDomain(), requestPayload.getCommand(), requestPayload, responsePayloadFactory);
    }

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param requestPayload
     *     the command payload
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <R>
     *     the type of the command payload
     * @param <T>
     *     the type of command response payload
     * @return the command response payload
     */
    <R extends JsonSerializableRequestPayload, T extends JsonDeserializableResponsePayload> T send(String domain, String command,
            R requestPayload, Supplier<T> responsePayloadFactory);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param sessionId
     *     the ID of the session in which to execute the command
     * @param requestPayload
     *     the command payload
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <R>
     *     the type of the command payload
     * @param <T>
     *     the type of command response payload
     * @return the command response payload
     */
    default <R extends JsonSerializableRequestPayload & CommandBoundPayload, T extends JsonDeserializableResponsePayload> T send(
            final String sessionId, final R requestPayload, final Supplier<T> responsePayloadFactory)
    {
        return this.send(requestPayload.getDomain(), requestPayload.getCommand(), sessionId, requestPayload, responsePayloadFactory);
    }

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param sessionId
     *     the ID of the session in which to execute the command
     * @param requestPayload
     *     the command payload
     * @param responsePayloadFactory
     *     the supplier/factory to retrieve or instantiate an instance of the command response payload type
     * @param <R>
     *     the type of the command payload
     * @param <T>
     *     the type of command response payload
     * @return the command response payload
     */
    <R extends JsonSerializableRequestPayload, T extends JsonDeserializableResponsePayload> T send(String domain, String command,
            String sessionId, R requestPayload, Supplier<T> responsePayloadFactory);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param requestPayload
     *     the command payload
     * @param <R>
     *     the type of the command payload
     */
    default <R extends JsonSerializableRequestPayload & CommandBoundPayload> void send(final R requestPayload)
    {
        this.send(requestPayload.getDomain(), requestPayload.getCommand(), requestPayload);
    }

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param requestPayload
     *     the command payload
     * @param <R>
     *     the type of the command payload
     */
    <R extends JsonSerializableRequestPayload> void send(String domain, String command, R requestPayload);

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param sessionId
     *     the ID of the session in which to execute the command
     * @param requestPayload
     *     the command payload
     * @param <R>
     *     the type of the command payload
     */
    default <R extends JsonSerializableRequestPayload & CommandBoundPayload> void send(final String sessionId, final R requestPayload)
    {
        this.send(requestPayload.getDomain(), requestPayload.getCommand(), sessionId, requestPayload);
    }

    /**
     * Sends an parameterised command to the connected Chrome / Chromium instance.
     *
     * @param domain
     *     the domain of the command
     * @param command
     *     the command
     * @param sessionId
     *     the ID of the session in which to execute the command
     * @param requestPayload
     *     the command payload
     * @param <R>
     *     the type of the command payload
     */
    <R extends JsonSerializableRequestPayload> void send(String domain, String command, String sessionId, R requestPayload);

    /**
     * Registers a global event listener.
     *
     * @param listener
     *     the listener to register
     */
    void registerListener(EventListener listener);

    /**
     * Registers a session-bound event listener.
     *
     * @param sessionId
     *     the ID of the session for which to register the listener - if the {@link #discardSessionData(String) session data is discarded},
     *     the listener will be deregistered as well, regardless of its last return value for to a
     *     {@link EventListener#eventReceived(String, String, String, JsonNode) received event}
     * @param listener
     *     the listener to register
     */
    void registerListener(String sessionId, EventListener listener);

    /**
     * Checks whether the client is connected.
     *
     * @return {@code true} if the client is connected, {@code false} if the web socket connection has been terminated for some reason
     * (e.g. remote Chrome instance failed)
     */
    boolean isConnected();

    /**
     * Attempts a reconnect.
     */
    void reconnect();

    /**
     * Shuts down this instance.
     */
    void shutdown();

    /**
     * Discards any internal data / registered listeners about a particular session.
     *
     * @param sessionId
     *     the session for which to discard data
     */
    void discardSessionData(String sessionId);

    /**
     * Instances of this interface may be used to listen to events emitted from the DevTools debugger.
     *
     * @author Axel Faust
     */
    @FunctionalInterface
    interface EventListener
    {

        /**
         * Handles an event.
         *
         * @param domain
         *     the domain of the event command
         * @param command
         *     the event command
         * @param sessionId
         *     the ID of the session in which the event was emitted, or {@code null} if it was emitted globally
         * @param payload
         *     the payload of the message
         * @return {@code true} whether this listener should continue to be informed about events, {@code false} if it should be discarded
         */
        boolean eventReceived(String domain, String command, String sessionId, JsonNode payload);

    }
}
