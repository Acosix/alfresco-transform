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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jetty.http.HttpStatus;

import de.acosix.alfresco.transform.base.TransformationException;

/**
 *
 * @author Axel Faust
 */
public class TokenManager
{

    private static final String COMMON_HEADER = "{\"alg\":\"HS256\", \"typ\": \"JWT\"}";

    private final String secret;

    public TokenManager(final String secret)
    {
        this.secret = secret;
    }

    public String createToken(final String payload)
    {
        try
        {
            final Encoder enc = Base64.getUrlEncoder();

            final String encodedHeader = enc.encodeToString(COMMON_HEADER.getBytes(StandardCharsets.UTF_8)).replace("=", "");
            final String encodedPayload = enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)).replace("=", "");

            final String hash = this.hash(encodedHeader, encodedPayload);
            final String token = encodedHeader + '.' + encodedPayload + '.' + hash;
            return token;
        }
        catch (final GeneralSecurityException e)
        {
            throw new TransformationException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Failed to generate token for request to OnlyOffice");
        }
    }

    public boolean isValidToken(final String token)
    {
        boolean validToken;
        final String[] fragments = token.split("\\.");

        if (fragments.length == 3)
        {
            final String providedHash = fragments[2];
            try
            {
                final String checkedHash = this.hash(fragments[0], fragments[1]);
                validToken = checkedHash.equals(providedHash);
            }
            catch (final GeneralSecurityException e)
            {
                validToken = false;
            }
        }
        else
        {
            validToken = false;
        }

        return validToken;
    }

    private String hash(final String encodedHeader, final String encodedPayload) throws NoSuchAlgorithmException, InvalidKeyException
    {
        final Encoder enc = Base64.getUrlEncoder();
        final String headerAndPayload = encodedHeader + '.' + encodedPayload;
        final Mac sha256 = Mac.getInstance("HmacSHA256");
        sha256.init(new SecretKeySpec(this.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        final String hash = enc.encodeToString(sha256.doFinal(headerAndPayload.getBytes(StandardCharsets.UTF_8))).replace("=", "");
        return hash;
    }
}
