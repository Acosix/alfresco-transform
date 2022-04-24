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
package de.acosix.alfresco.transform.misc.cdt;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * Encapsulates the parameters of a {@code Target.createTarget} command.
 *
 * @author Axel Faust
 */
public class CreateTargetParameters implements JsonSerializableRequestPayload, CommandBoundPayload
{

    private String url;

    private Integer width;

    private Integer height;

    private Boolean newWindow;

    private Boolean background;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain()
    {
        return "Target";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand()
    {
        return "createTarget";
    }

    /**
     * @param url
     *     the url to set
     */
    public void setUrl(final String url)
    {
        this.url = url;
    }

    /**
     * @param width
     *     the width to set
     */
    public void setWidth(final Integer width)
    {
        this.width = width;
    }

    /**
     * @param height
     *     the height to set
     */
    public void setHeight(final Integer height)
    {
        this.height = height;
    }

    /**
     * @param newWindow
     *     the newWindow to set
     */
    public void setNewWindow(final Boolean newWindow)
    {
        this.newWindow = newWindow;
    }

    /**
     * @param background
     *     the background to set
     */
    public void setBackground(final Boolean background)
    {
        this.background = background;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialise(final JsonGenerator jsonGenerator) throws IOException
    {
        jsonGenerator.writeStringField("url", this.url != null ? this.url : "");
        if (this.width != null)
        {
            jsonGenerator.writeNumberField("width", this.width);
        }
        if (this.height != null)
        {
            jsonGenerator.writeNumberField("height", this.height);
        }
        if (this.newWindow != null)
        {
            jsonGenerator.writeBooleanField("newWindow", this.newWindow);
        }
        if (this.background != null)
        {
            jsonGenerator.writeBooleanField("background", this.background);
        }
    }

}
