/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.service.transport.amqps;

import com.microsoft.azure.sdk.iot.deps.serializer.FileUploadNotificationParser;
import com.microsoft.azure.sdk.iot.service.DeliveryOutcome;
import com.microsoft.azure.sdk.iot.service.FileUploadNotification;
import com.microsoft.azure.sdk.iot.service.FileUploadNotificationListener;
import com.microsoft.azure.sdk.iot.service.IotHubServiceClientProtocol;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.message.Message;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class AmqpFileUploadNotificationReceivedHandler extends AmqpConnectionHandler
{
    protected static final String FILE_NOTIFICATION_RECEIVE_TAG = "filenotificationreceiver";
    protected static final String FILENOTIFICATION_ENDPOINT = "/messages/serviceBound/filenotifications";

    //This listener may be null
    private FileUploadNotificationListener listener;
    private AmqpFeedbackReceivedEvent amqpFeedbackReceivedEvent;

    protected AmqpFileUploadNotificationReceivedHandler(String hostName, String userName, String sasToken, IotHubServiceClientProtocol iotHubServiceClientProtocol, AmqpFeedbackReceivedEvent amqpFeedbackReceivedEvent, FileUploadNotificationListener listener)
    {
        super(hostName, userName, sasToken, iotHubServiceClientProtocol, FILE_NOTIFICATION_RECEIVE_TAG, FILENOTIFICATION_ENDPOINT, listener == null);

        if (amqpFeedbackReceivedEvent == null)
        {
            throw new IllegalArgumentException("Amqp feedback listener cannot be null");
        }

        this.listener = listener;
        this.amqpFeedbackReceivedEvent = amqpFeedbackReceivedEvent;
    }

    @Override
    public DeliveryOutcome onMessageArrived(Message message)
    {
        if (message.getBody() instanceof Data)
        {
            Data feedbackJson = (Data) message.getBody();
            String feedback = feedbackJson.getValue().toString();

            amqpFeedbackReceivedEvent.onFeedbackReceived(feedback);

            if (listener == null)
            {
                // If user isn't using listener for each file upload notification, then default behavior is to Complete all received
                // file upload notifications
                return DeliveryOutcome.Complete;
            }
            else
            {
                try
                {
                    FileUploadNotificationParser notificationParser = new FileUploadNotificationParser(feedback);
                    FileUploadNotification fileUploadNotification = new FileUploadNotification(notificationParser.getDeviceId(),
                            notificationParser.getBlobUri(), notificationParser.getBlobName(), notificationParser.getLastUpdatedTime(),
                            notificationParser.getBlobSizeInBytesTag(), notificationParser.getEnqueuedTimeUtc());

                    return listener.onFileUploadNotificationReceived(fileUploadNotification);
                }
                catch (IOException e)
                {
                    log.error("Failed to create file upload notification parser", e);
                }
            }
        }

        return DeliveryOutcome.Abandon;
    }

    @Override
    public void onMessageAcknowledged(DeliveryState deliveryState)
    {
        //Never called, do nothing
    }

    @Override
    public void openLinks(Session session, Map<Symbol, Object> properties)
    {
        Receiver notificationReceiver = session.receiver(tag);
        notificationReceiver.setProperties(properties);
        notificationReceiver.open();
    }
}