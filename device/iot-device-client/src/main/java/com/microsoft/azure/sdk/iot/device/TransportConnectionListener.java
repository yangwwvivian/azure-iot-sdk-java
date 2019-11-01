/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.device;

public interface TransportConnectionListener
{
    public void onTransportConnectionClosed();

    public void onTransportConnectionOpened();
}