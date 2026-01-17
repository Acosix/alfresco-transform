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
 * Encapsulates the definition of a viewport used as a parameter in some commands.
 *
 * @author Axel Faust
 */
public class Viewport implements JsonSerializableRequestPayload
{

    private final int x;

    private final int y;

    private final int width;

    private final int height;

    private final double scale;

    /**
     * Constructs a new viewport definition instance.
     *
     * @param x
     *     the x offset
     * @param y
     *     the y offset
     * @param width
     *     the width
     * @param height
     *     the height
     * @param scale
     *     the page scale factor
     */
    public Viewport(final int x, final int y, final int width, final int height, final double scale)
    {
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || scale <= 0)
        {
            throw new IllegalArgumentException("Viewport parameters cannot be negative, and width, height and scale cannot be zero");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
    }

    /**
     * @return the x offset
     */
    public int getX()
    {
        return this.x;
    }

    /**
     * @return the y offset
     */
    public int getY()
    {
        return this.y;
    }

    /**
     * @return the width
     */
    public int getWidth()
    {
        return this.width;
    }

    /**
     * @return the height
     */
    public int getHeight()
    {
        return this.height;
    }

    /**
     * @return the page scale factor
     */
    public double getScale()
    {
        return this.scale;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        jsonGenerator.writeNumberField("x", this.x);
        jsonGenerator.writeNumberField("y", this.y);
        jsonGenerator.writeNumberField("width", this.width);
        jsonGenerator.writeNumberField("height", this.height);
        jsonGenerator.writeNumberField("scale", this.scale);
    }

}
