/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.common.tests.longhaul;

import com.microsoft.azure.sdk.iot.common.helpers.Tools;
import com.microsoft.azure.sdk.iot.common.helpers.*;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.ModuleClientException;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.service.*;
import com.microsoft.azure.sdk.iot.service.auth.AuthenticationType;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import static com.microsoft.azure.sdk.iot.device.IotHubClientProtocol.*;
import static com.microsoft.azure.sdk.iot.service.auth.AuthenticationType.SAS;
import static com.microsoft.azure.sdk.iot.service.auth.AuthenticationType.SELF_SIGNED;
import static org.junit.Assert.fail;

public class LongHaulTests
{
    // How much to wait until a message makes it to the server, in milliseconds
    protected static final Integer SEND_TIMEOUT_MILLISECONDS = 60 * 1000;

    //How many milliseconds between retry
    protected static final Integer RETRY_MILLISECONDS = 100;
    private static final long SEND_MESSAGE_TIMEOUT = 20 * 1000;
    private static final long DELAY_BETWEEN_RANDOM_CLIENT_OPERATIONS = 1000;
    protected static String iotHubConnectionString;
    protected static String hostName;

    public LongHaulTestInstance testInstance;
    protected static RegistryManager registryManager;

    public LongHaulTests(IotHubClientProtocol protocol, AuthenticationType authenticationType, ClientType clientType, String publicKeyCert, String privateKey, String x509Thumbprint) throws Exception
    {
        this.testInstance = new LongHaulTestInstance(protocol, authenticationType, clientType, publicKeyCert, privateKey, x509Thumbprint);
    }

    protected static Collection inputsCommon() throws IOException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException, InterruptedException
    {
        X509CertificateGenerator certificateGenerator = new X509CertificateGenerator();
        return inputsCommon(certificateGenerator.getPublicCertificate(), certificateGenerator.getPrivateKey(), certificateGenerator.getX509Thumbprint(), ClientType.DEVICE_CLIENT, ClientType.MODULE_CLIENT);
    }

    protected static Collection inputsCommon(ClientType clientType) throws IOException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException, InterruptedException
    {
        X509CertificateGenerator certificateGenerator = new X509CertificateGenerator();
        return inputsCommon(certificateGenerator.getPublicCertificate(), certificateGenerator.getPrivateKey(), certificateGenerator.getX509Thumbprint(), clientType);
    }

    protected static Collection inputsCommon(String publicKeyCert, String privateKey, String x509Thumbprint, ClientType... clientTypes) throws IOException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException, InterruptedException
    {
        registryManager = RegistryManager.createFromConnectionString(iotHubConnectionString);
        hostName = IotHubConnectionStringBuilder.createConnectionString(iotHubConnectionString).getHostName();

        List inputs = new ArrayList();
        for (ClientType clientType : clientTypes)
        {
            if (clientType == ClientType.DEVICE_CLIENT)
            {
                inputs.addAll(Arrays.asList(
                        new Object[][]
                                {
                                        //sas token module client
                                        {MQTT, SAS, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS, SAS, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {MQTT_WS, SAS, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS_WS, SAS, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},

                                        //x509 module client
                                        {HTTPS, SELF_SIGNED, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {MQTT, SELF_SIGNED, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS, SELF_SIGNED, ClientType.DEVICE_CLIENT, publicKeyCert, privateKey, x509Thumbprint}
                                }
                ));
            }
            else
            {
                inputs.addAll(Arrays.asList(
                        new Object[][]
                                {
                                        //sas token module client
                                        {MQTT, SAS, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS, SAS, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {MQTT_WS, SAS, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS_WS, SAS, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},

                                        //x509 module client
                                        {MQTT, SELF_SIGNED, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint},
                                        {AMQPS, SELF_SIGNED, ClientType.MODULE_CLIENT, publicKeyCert, privateKey, x509Thumbprint}
                                }
                ));
            }
        }
        return inputs;
    }

    @Before
    public void setupTestInstance() throws Exception
    {
        this.testInstance.setup();
    }

    public class LongHaulTestInstance
    {
        public InternalClient client;
        public IotHubClientProtocol protocol;
        public BaseDevice identity;
        public AuthenticationType authenticationType;
        public ClientType clientType;
        public String publicKeyCert;
        public String privateKey;
        public String x509Thumbprint;

        public LongHaulTestInstance(IotHubClientProtocol protocol, AuthenticationType authenticationType, ClientType clientType, String publicKeyCert, String privateKey, String x509Thumbprint) throws Exception
        {
            this.protocol = protocol;
            this.authenticationType = authenticationType;
            this.clientType = clientType;
            this.publicKeyCert = publicKeyCert;
            this.privateKey = privateKey;
            this.x509Thumbprint = x509Thumbprint;
        }

        public void setup() throws Exception
        {
            String TEST_UUID = UUID.randomUUID().toString();

            /* Create unique device names */
            String deviceId = "java-longhaul-e2e-test-device".concat("-" + TEST_UUID);
            String moduleId = "java-longhaul-e2e-test-module".concat("-" + TEST_UUID);
            String deviceX509Id = "java-longhaul-e2e-test-device-x509".concat("-" + TEST_UUID);
            String moduleX509Id = "java-longhaul-e2e-test-module-x509".concat("-" + TEST_UUID);

            /* Create device on the service */
            Device device = Device.createFromId(deviceId, null, null);
            Module module = Module.createFromId(deviceId, moduleId, null);

            Device deviceX509 = Device.createDevice(deviceX509Id, AuthenticationType.SELF_SIGNED);
            deviceX509.setThumbprintFinal(x509Thumbprint, x509Thumbprint);
            Module moduleX509 = Module.createModule(deviceX509Id, moduleX509Id, AuthenticationType.SELF_SIGNED);
            moduleX509.setThumbprintFinal(x509Thumbprint, x509Thumbprint);
            device = com.microsoft.azure.sdk.iot.common.helpers.Tools.addDeviceWithRetry(registryManager, device);
            deviceX509 = com.microsoft.azure.sdk.iot.common.helpers.Tools.addDeviceWithRetry(registryManager, deviceX509);

            if (clientType == ClientType.DEVICE_CLIENT)
            {
                if (authenticationType == SAS)
                {
                    //sas device client
                    this.client = new DeviceClient(registryManager.getDeviceConnectionString(device), protocol);
                    this.identity = device;
                }
                else if (authenticationType == SELF_SIGNED)
                {
                    //x509 device client
                    this.client = new DeviceClient(registryManager.getDeviceConnectionString(deviceX509), protocol, publicKeyCert, false, privateKey, false);
                    this.identity = deviceX509;
                }
                else
                {
                    throw new Exception("Test code has not been written for this path yet");
                }
            }
            else if (clientType == ClientType.MODULE_CLIENT)
            {
                if (authenticationType == SAS)
                {
                    //sas module client
                    module = com.microsoft.azure.sdk.iot.common.helpers.Tools.addModuleWithRetry(registryManager, module);
                    this.client = new ModuleClient(DeviceConnectionString.get(iotHubConnectionString, device, module), protocol);
                    this.identity = module;
                }
                else if (authenticationType == SELF_SIGNED)
                {
                    //x509 module client
                    moduleX509 = Tools.addModuleWithRetry(registryManager, moduleX509);
                    this.client = new ModuleClient(DeviceConnectionString.get(iotHubConnectionString, deviceX509, moduleX509), protocol, publicKeyCert, false, privateKey, false);
                    this.identity = moduleX509;
                }
                else
                {
                    throw new Exception("Test code has not been written for this path yet");
                }
            }

            Thread.sleep(2000);

            if (clientType == ClientType.DEVICE_CLIENT && protocol == IotHubClientProtocol.HTTPS)
            {
                testInstance.client.setOption("SetMinimumPollingInterval", 50l);
            }
        }

        public void dispose()
        {
            try
            {
                this.client.closeNow();
                registryManager.removeDevice(this.identity.getDeviceId()); //removes all modules associated with this device, too
            }
            catch (Exception e)
            {
                //not a big deal if dispose fails. This test suite is not testing the functions in this cleanup.
                // If identities are left registered, they will be deleted my nightly cleanup job anyways
            }
        }

        public String getContextString()
        {
            return "Client type: " + this.clientType + ", Auth type: " + this.authenticationType + ", Protocol: " + this.protocol
                    + ", Identity details: " + (testInstance.clientType == ClientType.MODULE_CLIENT ? "DeviceId: " + testInstance.identity.getDeviceId()
                    + " ModuleId: " + ((Module) testInstance.identity).getId() : "DeviceId: " + testInstance.identity.getDeviceId());
        }

    }

    @After
    public void tearDownTest() throws IOException, IotHubException
    {
        this.testInstance.dispose();
    }

    private static String longHaulDurationEnvVarName = "IOTHUB_DEVICE_LONGHAUL_DURATION_SECONDS";
    private static long longHaulDurationMilliseconds = 60 * 60 * 1000L;

    public class ConnectionStatusChange
    {
        public IotHubConnectionStatus status;
        public IotHubConnectionStatusChangeReason reason;
        public Throwable throwable;

        public ConnectionStatusChange(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason reason, Throwable throwable)
        {
            this.status = status;
            this.reason = reason;
            this.throwable = throwable;
        }

        public void printConnectionStatusChange()
        {
            System.out.println();
            System.out.println("CONNECTION STATUS UPDATE: " + status);
            System.out.println("CONNECTION STATUS REASON: " + reason);
            System.out.println("CONNECTION STATUS THROWABLE: " + (throwable == null ? "null" : throwable.getMessage()));
            System.out.println();

            if (throwable != null)
            {
                throwable.printStackTrace();
            }
        }
    }

    public class ConnectionStatusChangeCallbackTracker implements IotHubConnectionStatusChangeCallback
    {
        List<ConnectionStatusChange> connectionStatusChanges;

        public ConnectionStatusChangeCallbackTracker(List<ConnectionStatusChange> connectionStatusChanges)
        {
            this.connectionStatusChanges = connectionStatusChanges;
        }

        @Override
        public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext)
        {
            this.connectionStatusChanges.add(new ConnectionStatusChange(status, statusChangeReason, throwable));
        }
    }

    @Test
    public void RandomPeriodicOperations()
    {
        List<ConnectionStatusChange> connectionStatusChanges = new ArrayList<>();
        Queue<Exception> exceptions = new ConcurrentLinkedQueue<>();
        testInstance.client.registerConnectionStatusChangeCallback(new ConnectionStatusChangeCallbackTracker(connectionStatusChanges), null);
        try
        {
            IotHubServicesCommon.openClientWithRetry(testInstance.client);
        }
        catch (InterruptedException e)
        {
            fail("Failed to open the client before starting the test");
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < longHaulDurationMilliseconds)
        {
            Exception e = randomClientOperation();
            if (e != null)
            {
                exceptions.add(e);
            }

            try
            {
                Thread.sleep(DELAY_BETWEEN_RANDOM_CLIENT_OPERATIONS);
            }
            catch (InterruptedException ex)
            {
                fail("Test was interrupted");
            }
        }

        System.out.println("Complete log of connection status changes: ");
        for (ConnectionStatusChange connectionStatusChange : connectionStatusChanges)
        {
            connectionStatusChange.printConnectionStatusChange();
            System.out.println();

            if (connectionStatusChange.status == IotHubConnectionStatus.DISCONNECTED)
            {
                fail("Device was disconnected before calling close");
            }

            if (connectionStatusChange.status == IotHubConnectionStatus.DISCONNECTED_RETRYING && (testInstance.protocol == AMQPS || testInstance.protocol == AMQPS_WS))
            {
                fail("Device briefly lost connectivity on a " + testInstance.protocol + " connection");
            }
        }

        System.out.println("Total list of exceptions during typical operations: ");
        for (Exception e : exceptions)
        {
            System.out.println("");
            System.out.println("#################");
            e.printStackTrace();
            System.out.println("#################");
            System.out.println("");
        }
    }

    private Exception randomClientOperation()
    {
        double random = new SecureRandom().nextDouble() * 100;

        if (random < 90)
        {
            return sendMessage();
        }
        else
        {
            return sendMessage();
        }
    }

    private Exception sendMessage()
    {
        try
        {
            Success success = new Success();
            success.setResult(false);
            Message message = new Message("testMessage" + System.currentTimeMillis());
            testInstance.client.sendEventAsync(message, new IotHubEventCallback()
            {
                @Override
                public void execute(IotHubStatusCode responseStatus, Object callbackContext) {
                    Success success1 = (Success) callbackContext;

                    if (responseStatus == IotHubStatusCode.OK_EMPTY || responseStatus == IotHubStatusCode.OK)
                    {
                        success1.setResult(true);
                        success1.setCallbackStatusCode(responseStatus);
                    }

                    success1.callbackWasFired();
                }
            }, success);

            long startTime = System.currentTimeMillis();
            while (!success.wasCallbackFired())
            {
                if (System.currentTimeMillis() - startTime > SEND_MESSAGE_TIMEOUT)
                {
                    throw new TimeoutException("Timed out waiting for callback on sent message");
                }

                Thread.sleep(300);
            }

            if (!success.getResult())
            {
                throw new Exception("Expected OK or OK_EMPTY callback after sending message but got " + success.getCallbackStatusCode());
            }
        }
        catch (Exception e)
        {
            return new Exception("Failed to send a message", e);
        }

        return null; //No exception occured during sending a message
    }
/*
    private Exception receiveMessage()
    {

    }

    private Exception sendReportedPropertyUpdate()
    {

    }

    private Exception receivedDesiredPropertyUpdate()
    {

    }

    private Exception receiveMethod()
    {

    }

    private Exception uploadFile()
    {
        try
        {
            if (testInstance.clientType == ClientType.DEVICE_CLIENT)
            {
                // arrange
                DeviceClient deviceClient = (DeviceClient) testInstance.client;

                // act
                deviceClient.uploadToBlobAsync(testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1].blobName, testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1].fileInputStream, testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1].fileLength, new FileUploadTests.FileUploadCallback(), testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1]);

                // assert
                FileUploadNotification fileUploadNotification = getFileUploadNotificationForThisDevice(deviceClient, MAX_FILES_TO_UPLOAD - 1);
                assertNotNull(buildExceptionMessage("file upload notification was null", deviceClient), fileUploadNotification);
                verifyNotification(fileUploadNotification, testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1], deviceClient);
                waitForFileUploadStatusCallbackTriggered(MAX_FILES_TO_UPLOAD - 1, deviceClient);
                assertEquals(buildExceptionMessage("File upload status should be SUCCESS but was " + testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1].fileUploadStatus, deviceClient), SUCCESS, testInstance.fileUploadState[MAX_FILES_TO_UPLOAD - 1].fileUploadStatus);
            }
        }
        catch (Exception e)
        {
            return e;
        }
    }
    */
}
