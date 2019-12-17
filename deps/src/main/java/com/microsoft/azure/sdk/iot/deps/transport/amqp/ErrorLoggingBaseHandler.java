/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.deps.transport.amqp;

import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Event;

@Slf4j
public class ErrorLoggingBaseHandler extends BaseHandler
{
    protected ProtonJExceptionParser protonJExceptionParser;

    @Override
    public void onTransportError(Event event)
    {
        protonJExceptionParser = new ProtonJExceptionParser(event);
        if (protonJExceptionParser.getError().equals(ProtonJExceptionParser.DEFAULT_ERROR))
        {
            log.warn("Amqp transport closed with an unknown exception");
        }
        else
        {
            log.warn("Amqp Transport Error with AMQP exception {} with description {}", protonJExceptionParser.getError(), protonJExceptionParser.getErrorDescription());
        }

    }
}
