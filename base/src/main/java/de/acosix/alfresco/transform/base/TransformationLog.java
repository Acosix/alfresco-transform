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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Instances of this interface provide transformation log recording and retrieval capabilities for a transformer application.
 *
 * @author Axel Faust
 */
public interface TransformationLog
{

    /**
     * Retrieves the most recent log entries in descending order of request completion.
     *
     * @param count
     *            the limit / count of entries to retrieve
     * @return the list of entries
     */
    List<Entry> getMostRecentEntries(int count);

    /**
     * Retrieves the most recent log entries in descending order of request completion, limited to those entries created by the current
     * host. Depending on the implementation of the transformation log, the result may be different to {@link #getMostRecentEntries(int) the
     * more general entry retrieval} when multiple transformers operate in a cluster-like constellation and share log data.
     *
     * @param count
     *            the limit / count of entries to retrieve
     * @return the list of entries
     */
    List<Entry> getMostRecentHostEntries(int count);

    /**
     * Starts a new transformation log entry for the {@link Thread#currentThread() current thread}. This operation fails if a log entry has
     * already been started on the current thread, so callers must make sure to properly {@link #closeCurrentEntry() close the current
     * entry} when a transformation has completed or failed.
     *
     * @return the log entry
     */
    MutableEntry startNewEntry();

    /**
     * Retrieves the active transformation log entry for the {@link Thread#currentThread() current thread}.
     *
     * @return the log entry
     */
    Optional<MutableEntry> getCurrentEntry();

    /**
     * Closes the {@link #getCurrentEntry() active transformation log entry} for the {@link Thread#currentThread() current thread}.
     */
    void closeCurrentEntry();

    /**
     * Instances of this interface represent immutable transformation log entries.
     *
     * @author Axel Faust
     */
    interface Entry
    {

        /**
         * Retrieves the host on which the transformation took place.
         *
         * @return the name of the host
         */
        String getHost();

        /**
         * Retrieves the host-local sequence number of transformations
         *
         * @return the sequence number for this transformation
         */
        int getSequenceNumber();

        /**
         * Retrieves the timestamp the log entry was created / the transformation processing started.
         *
         * @return the start timestamp
         */
        long getStartTime();

        /**
         * Retrieves the timestamp the log entry was {@link TransformationLog#closeCurrentEntry() closed} / processing ended.
         *
         * @return the end timestamp
         */
        long getEndTime();

        /**
         * Retrieves the HTTP / transformation status code used to denote the result of the transformation.
         *
         * @return the HTTP / transformation status code
         */
        int getStatusCode();

        /**
         * Retrieves the status message provided to add further detail to the result of the transformation.
         *
         * @return the status message
         */
        String getStatusMessage();

        /**
         * Retrieves the amount of time spent in handling the request until the core transformation handling commenced.
         *
         * @return the duration in milliseconds
         */
        long getRequestHandlingDuration();

        /**
         * Retrieves the amount of time spent in the core transformation handling.
         *
         * @return the duration in milliseconds or {@code -1} if the request failed before the core transformation handling
         */
        long getTransformationDuration();

        /**
         * Retrieves the amount of time spent in handling the the result of a transformation and response to caller.
         *
         * @return the duration in milliseconds or {@code -1} if the request failed before the handling of the transformation result /
         *         response
         */
        long getResponseHandlingDuration();

        /**
         * Retrieves the mimetype of the source file
         *
         * @return the source mimetype
         */
        String getSourceMimetype();

        /**
         * Retrieves the size of the source file.
         *
         * @return the size in bytes or {@code -1} if the transformation request failed before the source file size was (reliably) known
         */
        long getSourceSize();

        /**
         * Retrieves the mimetype of the target file.
         *
         * @return the target mimetype
         */
        String getTargetMimetype();

        /**
         * Retrieves the size of the result file after transformation.
         *
         * @return the size in bytes or {@code -1} if the transformation request failed before a result file was created
         */
        long getResultSize();

        /**
         * Retrieves the name of the transformer picked to perform the transformation.
         *
         * @return the name of the transformer or {@code null} if a transformer could not be determined
         */
        String getTransformerName();

        /**
         * Retrieves the options specified by the caller within the transformation request, without any transformer-specific default
         * options.
         *
         * @return the original transformation options
         */
        Map<String, String> getOptions();
    }

    /**
     * Instances of this interface represent mutable transformation log entries. Typically, only the
     * {@link TransformationLog#getCurrentEntry() current log entry} will be mutable.
     *
     * @author Axel Faust
     */
    interface MutableEntry extends Entry
    {

        /**
         * Sets the overall status for this log entry.
         *
         * @param statusCode
         *            the status code (based on HTTP status codes)
         */
        void setStatus(int statusCode);

        /**
         * Sets the overall status and optional detail message for this log entry.
         *
         * @param statusCode
         *            the status code (based on HTTP status codes)
         * @param statusMessage
         *            the detail message for the status or {@code null} if no additional detail is applicable / required
         */
        void setStatus(int statusCode, String statusMessage);

        /**
         * Marks the end of the transformation request and start of core transformation handling, updating the appropriate internal
         * timestamp.
         */
        void markStartOfTransformation();

        /**
         * Marks the end of the core transformation handling, updating the appropriate internal timestamp.
         */
        void markEndOfTransformation();

        /**
         * Records the request-provided values for this log entry.
         *
         * @param sourceMimetype
         *            the specified source file mimetype
         * @param sourceSize
         *            the source file size in bytes
         * @param targetMimetype
         *            the specified target file mimetype
         * @param options
         *            the transformation options for the request
         */
        void recordRequestValues(String sourceMimetype, long sourceSize, String targetMimetype, Map<String, String> options);

        /**
         * Records the name of the selected transformer.
         *
         * @param transformerName
         *            the name of the selected transformer
         */
        void recordSelectedTransformer(String transformerName);

        /**
         * Records the size of the result file after transformation.
         *
         * @param resultSize
         *            the size of the result file in bytes
         */
        void recordResultSize(long resultSize);
    }
}
