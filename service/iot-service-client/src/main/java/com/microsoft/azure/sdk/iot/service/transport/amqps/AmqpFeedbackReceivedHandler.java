/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.service.transport.amqps;

import com.microsoft.azure.sdk.iot.service.*;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.message.Message;

import java.util.Map;

public class AmqpFeedbackReceivedHandler extends AmqpConnectionHandler
{
    public static final String RECEIVE_TAG = "receiver";
    public static final String ENDPOINT = "/messages/servicebound/feedback";

    private FeedbackBatchMessageListener listener;
    private AmqpFeedbackReceivedEvent amqpFeedbackReceivedEvent;

    /**
     * Constructor to set up connection parameters and initialize
     * handshaker and flow controller for transport
     * @param hostName The address string of the service (example: AAA.BBB.CCC)
     * @param userName The username string to use SASL authentication (example: user@sas.service)
     * @param sasToken The SAS token string
     * @param iotHubServiceClientProtocol protocol to use
     * @param listener callback to delegate the received message to the user API
     */
    public AmqpFeedbackReceivedHandler(String hostName, String userName, String sasToken, IotHubServiceClientProtocol iotHubServiceClientProtocol, AmqpFeedbackReceivedEvent amqpFeedbackReceivedEvent, FeedbackBatchMessageListener listener)
    {
        super(hostName, userName, sasToken, iotHubServiceClientProtocol, RECEIVE_TAG, ENDPOINT, listener == null);

        if (amqpFeedbackReceivedEvent == null)
        {
            throw new IllegalArgumentException("amqpFeedbackReceivedEvent cannot be null");
        }

        this.listener = listener;
        this.amqpFeedbackReceivedEvent = amqpFeedbackReceivedEvent;
    }

    @Override
    public DeliveryOutcome onMessageArrived(Message message)
    {
        String feedbackString = message.getBody().toString();
        amqpFeedbackReceivedEvent.onFeedbackReceived(feedbackString);

        if (listener == null)
        {
            // If no listener was provided, the default behavior is to Complete all received feedback messages
            return DeliveryOutcome.Complete;
        }
        else
        {
            FeedbackBatch feedbackBatch = FeedbackBatchMessage.parse(feedbackString);
            return listener.onFeedbackMessageReceived(feedbackBatch);
        }
    }

    @Override
    public void onMessageAcknowledged(DeliveryState deliveryState)
    {
        //Never called, do nothing
    }

    @Override
    public void openLinks(Session session, Map<Symbol, Object> properties)
    {
        Receiver feedbackReceiver = session.receiver(tag);
        feedbackReceiver.setProperties(properties);
        feedbackReceiver.open();
    }
}