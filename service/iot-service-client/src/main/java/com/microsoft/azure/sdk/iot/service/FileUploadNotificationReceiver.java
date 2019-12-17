/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.service;

import com.microsoft.azure.sdk.iot.service.transport.amqps.AmqpFileUploadNotificationReceive;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileUploadNotificationReceiver extends Receiver
{
    private final long DEFAULT_TIMEOUT_MS = 60000;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private AmqpFileUploadNotificationReceive amqpFileUploadNotificationReceive;

    /**
     * Constructor to verify initialization parameters
     * Create instance of AmqpReceive
     * @param hostName The iot hub host name
     * @param userName The iot hub user name
     * @param sasToken The iot hub SAS token for the given device
     * @param iotHubServiceClientProtocol The iot hub protocol name
     */
    FileUploadNotificationReceiver(String hostName, String userName, String sasToken, IotHubServiceClientProtocol iotHubServiceClientProtocol)
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_001: [** The constructor shall throw IllegalArgumentException if any the input string is null or empty **]**
        if (Tools.isNullOrEmpty(hostName))
        {
            throw new IllegalArgumentException("hostName cannot be null or empty");
        }
        if (Tools.isNullOrEmpty(userName))
        {
            throw new IllegalArgumentException("userName cannot be null or empty");
        }
        if (Tools.isNullOrEmpty(sasToken))
        {
            throw new IllegalArgumentException("sasToken cannot be null or empty");
        }
        if (iotHubServiceClientProtocol  == null)
        {
            throw new IllegalArgumentException("iotHubServiceClientProtocol cannot be null");
        }

        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_002: [** The constructor shall create a new instance of AmqpFileUploadNotificationReceive object **]**
        this.amqpFileUploadNotificationReceive = new AmqpFileUploadNotificationReceive(hostName, userName, sasToken, iotHubServiceClientProtocol);
    }

    /**
     * Open AmqpReceive object
     * @deprecated This method does nothing anymore
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     */
    @Deprecated
    public void open() throws IOException
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_004: [** The function shall call open() on the member AmqpFileUploadNotificationReceive object **]**
        this.amqpFileUploadNotificationReceive.open();
    }

    /**
     * Close AmqpReceive object
     * @deprecated This method does nothing anymore
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     */
    @Deprecated
    public void close() throws IOException
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_006: [** The function shall call close() on the member AmqpFileUploadNotificationReceive object **]**
        this.amqpFileUploadNotificationReceive.close();
    }

    /**
     * Receive FileUploadNotification with default timeout
     *
     * QoS for receiving file upload notifications is at least once
     *
     * @deprecated Use {@link #receive(FileUploadNotificationListener)} instead
     * @return The received FileUploadNotification object
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     * @throws InterruptedException This exception is thrown if the receive process has been interrupted
     */
    @Deprecated
    public FileUploadNotification receive() throws IOException, InterruptedException
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_007: [** The function shall call receive(long timeoutMs) function with the default timeout **]**
        return receive(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Receive FileUploadNotification with specific timeout
     *
     * QoS for receiving file upload notifications is at least once
     *
     * @deprecated Use {@link #receive(FileUploadNotificationListener)} instead
     * @param timeoutMs The timeout in milliseconds
     * @return The received FileUploadNotification object
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     * @throws InterruptedException This exception is thrown if the receive process has been interrupted
     */
    @Deprecated
    public FileUploadNotification receive(long timeoutMs) throws IOException, InterruptedException
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_008: [** The function shall throw IOException if the member AmqpFileUploadNotificationReceive object has not been initialized **]**
        if (this.amqpFileUploadNotificationReceive == null)
        {
            throw new IOException("AMQP receiver is not initialized");
        }
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_009: [** The function shall call receive() on the member AmqpFileUploadNotificationReceive object and return with the result **]**
        return this.amqpFileUploadNotificationReceive.receive(timeoutMs);
    }

    /**
     * Async wrapper for open() operation
     * @deprecated This method does nothing anymore
     * @return The future object for the requested operation
     */
    @Deprecated
    @Override
    public CompletableFuture<Void> openAsync()
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_010: [** The function shall create an async wrapper around the open() function call **]**
        final CompletableFuture<Void> future = new CompletableFuture<>();
        executor.submit(() -> {
            try
            {
                open();
                future.complete(null);
            } catch (IOException e)
            {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Async wrapper for close() operation
     * @deprecated This method does nothing anymore
     * @return The future object for the requested operation
     */
    @Deprecated
    @Override
    public CompletableFuture<Void> closeAsync()
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_011: [** The function shall create an async wrapper around the close() function call **]**
        final CompletableFuture<Void> future = new CompletableFuture<>();
        executor.submit(() -> {
            try
            {
                close();
                future.complete(null);
            } catch (IOException e)
            {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Async wrapper for receive() operation with default timeout
     *
     * QoS for receiving file upload notifications is at least once
     *
     * @deprecated Use {@link #receive(FileUploadNotificationListener)} instead
     * @return The future object for the requested operation
     */
    @Deprecated
    @Override
    public CompletableFuture<FileUploadNotification> receiveAsync()
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_012: [** The function shall create an async wrapper around the receive() function call using the default timeout **]**
        return receiveAsync(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Async wrapper for receive() operation with specific timeout
     *
     * QoS for receiving file upload notifications is at least once
     * @deprecated Use {@link #receive(FileUploadNotificationListener)} instead
     * @return The future object for the requested operation
     */
    @Deprecated
    @Override
    public CompletableFuture<FileUploadNotification> receiveAsync(long timeoutMs)
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_013: [** The function shall create an async wrapper around the receive(long timeoutMs) function call **]**
        final CompletableFuture<FileUploadNotification> future = new CompletableFuture<>();
        executor.submit(() -> {
            try
            {
                FileUploadNotification responseFileUploadNotification = receive(timeoutMs);
                future.complete(responseFileUploadNotification);
            } catch (IOException e)
            {
                future.completeExceptionally(e);
            } catch (InterruptedException e)
            {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Receive File upload notifications with specific timeout. This method does not require {@link #open()} to be called beforehand. This method will open
     * the connection, listen for file upload notifications for the default amount of time in milliseconds({@link #DEFAULT_TIMEOUT_MS}), and then close itself. There is no need to call {@link #close()} after
     * calling this method as this method closes itself after the timeout
     *
     * QoS for receiving file upload notifications is at least once
     *
     * @param fileUploadNotificationListener the listener that will be called each time a file upload notification is received to allow you to Complete/Abandon/Reject the notification
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     * @throws InterruptedException This exception is thrown if the receive process has been interrupted
     */
    public void receive(FileUploadNotificationListener fileUploadNotificationListener) throws IOException, InterruptedException
    {
        this.receive(fileUploadNotificationListener, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Receive File upload notifications with specific timeout. This method does not require {@link #open()} to be called beforehand. This method will open
     * the connection, listen for file upload notifications for the specified amount of time in milliseconds({@link #DEFAULT_TIMEOUT_MS}), and then close itself. There is no need to call {@link #close()} after
     * calling this method as this method closes itself after the timeout
     *
     * QoS for receiving file upload notifications is at least once
     *
     * @param timeoutMs The timeout in milliseconds
     * @param fileUploadNotificationListener the listener that will be called each time a file upload notification is received to allow you to Complete/Abandon/Reject the notification
     * @throws IOException This exception is thrown if the input AmqpReceive object is null
     * @throws InterruptedException This exception is thrown if the receive process has been interrupted
     */
    public void receive(FileUploadNotificationListener fileUploadNotificationListener, long timeoutMs) throws IOException, InterruptedException
    {
        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_008: [** The function shall throw IOException if the member AmqpFileUploadNotificationReceive object has not been initialized **]**
        if (this.amqpFileUploadNotificationReceive == null)
        {
            throw new IOException("AMQP receiver is not initialized");
        }

        // Codes_SRS_SERVICE_SDK_JAVA_FILEUPLOADNOTIFICATIONRECEIVER_25_009: [** The function shall call receive() on the member AmqpFileUploadNotificationReceive object and return with the result **]**
        this.amqpFileUploadNotificationReceive.receive(timeoutMs, fileUploadNotificationListener);
    }
}
