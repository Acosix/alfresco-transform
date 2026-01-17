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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Encapsulates the parameters of a {@code Page.captureScreenshot} command.
 *
 * @author Axel Faust
 */
public class CaptureScreenshotParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private Format format;

    private Double quality;

    private Viewport clip;

    private Boolean fromSurface;

    private Boolean captureBeyondViewport;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain()
    {
        return "Page";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand()
    {
        return "captureScreenshot";
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        if (this.format != null)
        {
            jsonGenerator.writeStringField("format", this.format.name());

            if (this.format == Format.jpeg && this.quality != null)
            {
                jsonGenerator.writeNumberField("quality", this.quality);
            }
        }
        if (this.clip != null)
        {
            jsonGenerator.writeObjectFieldStart("clip");
            this.clip.serialise(jsonGenerator);
            jsonGenerator.writeEndObject();
        }
        if (this.fromSurface != null)
        {
            jsonGenerator.writeBooleanField("fromSurface", this.fromSurface);
        }
        if (this.captureBeyondViewport != null)
        {
            jsonGenerator.writeBooleanField("captureBeyondViewport", this.captureBeyondViewport);
        }
    }

    /**
     * @param format
     *     the format to set
     */
    public void setFormat(final Format format)
    {
        this.format = format;
    }

    /**
     * @param quality
     *     the quality to set
     */
    public void setQuality(final Double quality)
    {
        if (quality != null && (quality < 0 || quality > 100))
        {
            throw new IllegalArgumentException("Screenshot JPEG quality cannot be outside the [0..100] range");
        }
        this.quality = quality;
    }

    /**
     * @param clip
     *     the clip to set
     */
    public void setClip(final Viewport clip)
    {
        this.clip = clip;
    }

    /**
     * @param fromSurface
     *     the fromSurface to set
     */
    public void setFromSurface(final Boolean fromSurface)
    {
        this.fromSurface = fromSurface;
    }

    /**
     * @param captureBeyondViewport
     *     the captureBeyondViewport to set
     */
    public void setCaptureBeyondViewport(final Boolean captureBeyondViewport)
    {
        this.captureBeyondViewport = captureBeyondViewport;
    }

    public enum Format
    {
        jpeg,
        png;
    }
}
