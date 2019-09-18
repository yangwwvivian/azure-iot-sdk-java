// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package samples.com.microsoft.azure.sdk.iot;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.service.Device;
import com.microsoft.azure.sdk.iot.service.RegistryManager;
import com.microsoft.azure.sdk.iot.service.auth.AuthenticationType;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import org.slf4j.impl.SimpleLogger;

import java.io.Console;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.UUID;


/** Sends a number of event messages to an IoT Hub. */
public class SendEventLonghaul
{
    static boolean deviceIsConnected = false;
    static boolean deviceIsReconnecting = false;
    static boolean keyboardInterrupted = false;
    static double startTime;
    static double stopTime;
    static double reconnectingTimeMillis = 0;
    static double reconnectingUpdateCount = 0;
    static double reconnectingStartTime = 0;
    static double reconnectingStopTime = 0;
    static double maxReconnectTime = 0;

    protected static class IotHubConnectionStatusChangeCallbackLogger implements IotHubConnectionStatusChangeCallback
    {
        @Override
        public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext)
        {
            System.out.println();
            System.out.println("CONNECTION STATUS UPDATE: " + status);
            System.out.println("CONNECTION STATUS REASON: " + statusChangeReason);
            System.out.println("CONNECTION STATUS THROWABLE: " + (throwable == null ? "null" : throwable.getMessage()));
            System.out.println();

            if (throwable != null)
            {
                throwable.printStackTrace();
            }

            if (status == IotHubConnectionStatus.DISCONNECTED)
            {
                //connection was lost, and is not being re-established. Look at provided exception for
                // how to resolve this issue. Cannot send messages until this issue is resolved, and you manually
                // re-open the device client
                deviceIsConnected = false;
                deviceIsReconnecting = false;
                reconnectingStopTime = System.currentTimeMillis();
            }
            else if (status == IotHubConnectionStatus.DISCONNECTED_RETRYING)
            {
                //connection was lost, but is being re-established. Can still send messages, but they won't
                // be sent until the connection is re-established
                deviceIsReconnecting = true;
                deviceIsConnected = false;
                reconnectingUpdateCount++;
                reconnectingStartTime = System.currentTimeMillis();
            }
            else if (status == IotHubConnectionStatus.CONNECTED)
            {
                deviceIsConnected = true;
                deviceIsReconnecting = false;
                reconnectingStopTime = System.currentTimeMillis();

                if (reconnectingStartTime != 0)
                {
                    double previousReconnectTime = reconnectingStopTime - reconnectingStartTime;
                    reconnectingTimeMillis +=  previousReconnectTime;

                    if (previousReconnectTime > maxReconnectTime)
                    {
                        maxReconnectTime = previousReconnectTime;
                    }

                    reconnectingStartTime = 0;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, IotHubException
    {
        if (args.length != 2 && args.length != 3)
        {
            System.out.format(
                    "Expected 2 or 3 arguments but received: %d.\n"
                            + "1.) Iot hub connection string \n"
                            + "2.) Protocol (amqps, amqps_ws, mqtt, mqtt_ws, https)\n"
                            + "3.) (Optional) Log level (INFO, DEBUG, TRACE)",
                    args.length);
            return;
        }

        String connectionString = args[0];
        String protocolString = args[1];
        IotHubClientProtocol protocol = null;
        if (protocolString.equalsIgnoreCase("amqps"))
        {
            protocol = IotHubClientProtocol.AMQPS;
        }
        else if (protocolString.equalsIgnoreCase("amqps_ws"))
        {
            protocol = IotHubClientProtocol.AMQPS_WS;
        }
        else if (protocolString.equalsIgnoreCase("mqtt"))
        {
            protocol = IotHubClientProtocol.MQTT;
        }
        else if (protocolString.equalsIgnoreCase("mqtt_ws"))
        {
            protocol = IotHubClientProtocol.MQTT_WS;
        }
        else if (protocolString.equalsIgnoreCase("https"))
        {
            protocol = IotHubClientProtocol.HTTPS;
        }

        new Thread(new KeyboardInterrupt()).start();

        if (args.length == 3)
        {
            System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, args[2]);
            System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
            System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss:SSS");
        }

        RegistryManager registryManager = RegistryManager.createFromConnectionString(connectionString);
        String deviceId = "java-longhaul-e2e-test-device".concat("-" + UUID.randomUUID());
        Device device = Device.createDevice(deviceId, AuthenticationType.SAS);

        try
        {
            System.out.println("Adding device " + deviceId);
            device = registryManager.addDevice(device);
            DeviceClient client = new DeviceClient(registryManager.getDeviceConnectionString(device), protocol);

            client.registerConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallbackLogger(), null);

            System.out.println("Opening device client");
            client.open();

            long startTime = System.currentTimeMillis();
            while ((deviceIsConnected || deviceIsReconnecting) && !keyboardInterrupted)
            {
                Thread.sleep(2000);
                client.sendEventAsync(new Message("asdf"), new IotHubEventCallback()
                {
                    @Override
                    public void execute(IotHubStatusCode responseStatus, Object callbackContext)
                    {

                    }
                }, null);
            }
            long stopTime = System.currentTimeMillis();

            try
            {
                client.closeNow();
            }
            catch (Exception e)
            {
                //don't care
            }

            double secondsSpentOnlineBeforeDisconnect = (stopTime - startTime) / 1000.0;

            if (keyboardInterrupted)
            {
                System.out.println(String.format("Device %s lost connection after %f seconds due to a keyboard interrupt", deviceId, secondsSpentOnlineBeforeDisconnect));
            }
            else
            {
                System.out.println(String.format("Device %s lost connection after %f seconds due to a terminal exception", deviceId, secondsSpentOnlineBeforeDisconnect));
            }
            System.out.println();

            System.out.println();
            System.out.println("Statistics");
            System.out.println();
            System.out.println("Total seconds test was run for: " + secondsSpentOnlineBeforeDisconnect);
            double totalTimeConnected = (secondsSpentOnlineBeforeDisconnect - (reconnectingTimeMillis / 1000.0));
            System.out.println("Total seconds connected: " + totalTimeConnected);
            double percentOfTimeConnected = 100 * totalTimeConnected / secondsSpentOnlineBeforeDisconnect;
            System.out.println("Percent of time connected: " + percentOfTimeConnected + "%");
            System.out.println("Number of disconnected retrying events: " + reconnectingUpdateCount);
            System.out.println("Average time in seconds to reconnect: " + ((reconnectingTimeMillis / 1000.0) / reconnectingUpdateCount));
            System.out.println("Longest time in seconds to reconnect: " + (maxReconnectTime / 1000.0));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        System.out.println("Removing device " + deviceId);
        registryManager.removeDevice(device);
    }

    static class KeyboardInterrupt implements Runnable
    {
        @Override
        public void run()
        {
            Scanner keyboard = new Scanner(System.in);
            keyboard.nextLine();
            keyboardInterrupted = true;
            System.out.println("Exiting from keyboard interrupt");
        }
    }
}
