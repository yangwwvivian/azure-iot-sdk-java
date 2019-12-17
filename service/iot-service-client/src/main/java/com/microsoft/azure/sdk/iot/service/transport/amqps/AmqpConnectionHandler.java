/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.service.transport.amqps;

import com.microsoft.azure.sdk.iot.deps.auth.IotHubSSLContext;
import com.microsoft.azure.sdk.iot.deps.transport.amqp.ErrorLoggingBaseHandler;
import com.microsoft.azure.sdk.iot.deps.ws.impl.WebSocketImpl;
import com.microsoft.azure.sdk.iot.service.DeliveryOutcome;
import com.microsoft.azure.sdk.iot.service.IotHubServiceClientProtocol;
import com.microsoft.azure.sdk.iot.service.Tools;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.transport.TransportUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.*;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.*;
import org.apache.qpid.proton.engine.impl.TransportInternal;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.reactor.FlowController;
import org.apache.qpid.proton.reactor.Handshaker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AmqpConnectionHandler extends ErrorLoggingBaseHandler
{
    protected String tag;
    protected String endpoint;

    protected static final String SEND_PORT_AMQPS = ":5671";
    protected static final String SEND_PORT_AMQPS_WS = ":443";
    protected static final String WEBSOCKET_PATH = "/$iothub/websocket";
    protected static final String WEBSOCKET_SUB_PROTOCOL = "AMQPWSB10";

    protected final String hostName;
    protected final String userName;
    protected final String sasToken;

    protected int nextSendTag = 0;

    private final IotHubServiceClientProtocol iotHubServiceClientProtocol;
    private final String webSocketHostName;

    protected Exception savedException;

    private boolean connectionWasOpened = false;
    private boolean closeAfterSingleMessage;

    /**
     * Constructor to set up connection parameters and initialize
     * handshaker and flow controller for transport
     * @param hostName The address string of the service (example: AAA.BBB.CCC)
     * @param userName The username string to use SASL authentication (example: user@sas.service)
     * @param sasToken The SAS token string
     */
    AmqpConnectionHandler(String hostName, String userName, String sasToken, IotHubServiceClientProtocol iotHubServiceClientProtocol, String tag, String endpoint, boolean closeAfterSingleMessage)
    {
        if (Tools.isNullOrEmpty(hostName)
                || Tools.isNullOrEmpty(userName)
                || Tools.isNullOrEmpty(sasToken)
                || iotHubServiceClientProtocol == null
                || Tools.isNullOrEmpty(tag)
                || Tools.isNullOrEmpty(endpoint))
        {
            throw new IllegalArgumentException("Input parameters cannot be null or empty");
        }
        this.iotHubServiceClientProtocol = iotHubServiceClientProtocol;
        this.webSocketHostName = hostName;
        if (this.iotHubServiceClientProtocol == IotHubServiceClientProtocol.AMQPS_WS)
        {
            this.hostName = hostName + SEND_PORT_AMQPS_WS;
        }
        else
        {
            this.hostName = hostName + SEND_PORT_AMQPS;
        }

        this.userName = userName;
        this.sasToken = sasToken;

        this.tag = tag;
        this.endpoint = endpoint;

        add(new Handshaker());
        add(new FlowController());

        this.closeAfterSingleMessage = closeAfterSingleMessage;
    }

    /**
     * Create Proton SslDomain object from Address using the given Ssl mode
     * @param mode Proton enum value of requested Ssl mode
     * @return The created Ssl domain
     */
    private SslDomain makeDomain(SslDomain.Mode mode)
    {
        SslDomain domain = Proton.sslDomain();

        try
        {
            // Need the base trusted certs for IotHub in our ssl context. IotHubSSLContext handles that
            domain.setSslContext(new IotHubSSLContext().getSSLContext());
        }
        catch (Exception e)
        {
            this.savedException = e;
        }

        domain.init(mode);

        return domain;
    }

    /**
     * Event handler for the on delivery event
     * @param event The proton event object
     */
    @Override
    public void onDelivery(Event event)
    {
        if (event.getLink() instanceof Receiver)
        {
            Receiver recv = (Receiver)event.getLink();
            Delivery delivery = recv.current();

            if (delivery.isReadable() && !delivery.isPartial() && delivery.getLink().getName().equalsIgnoreCase(tag))
            {
                int size = delivery.pending();
                byte[] buffer = new byte[size];
                int read = recv.recv(buffer, 0, buffer.length);
                recv.advance();

                org.apache.qpid.proton.message.Message msg = Proton.message();
                msg.decode(buffer, 0, read);

                DeliveryOutcome outcome = onMessageArrived(msg);

                if (outcome == DeliveryOutcome.Complete)
                {
                    delivery.disposition(Accepted.getInstance());
                    delivery.settle();

                }
                else if (outcome == DeliveryOutcome.Abandon)
                {
                    delivery.disposition(Released.getInstance());
                    delivery.settle();
                }
                else if (outcome == DeliveryOutcome.Reject)
                {
                    //purposefully do nothing. Service will treat unack'd messages as rejected
                }

                // Old API behavior dictated that the amqp connection close upon receiving one feedback message or file upload notification,
                // but new APIs should allow the amqp connection to stay open and receive multiple messages
                if (closeAfterSingleMessage)
                {
                    recv.getSession().close();
                    recv.getSession().getConnection().close();
                }
            }
        }
        else //Sender link received ack for sent message
        {
            Delivery d = event.getDelivery();

            DeliveryState remoteState = d.getRemoteState();

            onMessageAcknowledged(remoteState);

            d.settle();

            Sender snd = event.getSender();

            // Old API behavior dictated that the amqp connection close upon sending one C2D message,
            // but new APIs should allow the amqp connection to stay open and receive multiple messages
            if (closeAfterSingleMessage)
            {
                snd.close();
                snd.getSession().close();
                snd.getSession().getConnection().close();
            }
        }
    }

    public abstract DeliveryOutcome onMessageArrived(Message message);
    public abstract void onMessageAcknowledged(DeliveryState deliveryState);
    public abstract void openLinks(Session event, Map<Symbol, Object> properties);

    @Override
    public void onConnectionBound(Event event)
    {
        Transport transport = event.getConnection().getTransport();
        if (transport != null)
        {
            if (this.iotHubServiceClientProtocol == IotHubServiceClientProtocol.AMQPS_WS)
            {
                WebSocketImpl webSocket = new WebSocketImpl();
                webSocket.configure(this.webSocketHostName, WEBSOCKET_PATH, 0, WEBSOCKET_SUB_PROTOCOL, null, null);
                ((TransportInternal)transport).addTransportLayer(webSocket);
            }
            Sasl sasl = transport.sasl();
            sasl.plain(this.userName, this.sasToken);

            SslDomain domain = makeDomain(SslDomain.Mode.CLIENT);
            domain.setPeerAuthentication(SslDomain.VerifyMode.VERIFY_PEER);
            transport.ssl(domain);
        }
    }

    @Override
    public void onConnectionInit(Event event)
    {
        Connection conn = event.getConnection();
        conn.setHostname(hostName);
        Session ssn = conn.session();

        Map<Symbol, Object> properties = new HashMap<>();
        properties.put(Symbol.getSymbol(TransportUtils.versionIdentifierKey), TransportUtils.USER_AGENT_STRING);

        conn.open();
        ssn.open();
        openLinks(ssn, properties);
    }

    @Override
    public void onLinkInit(Event event)
    {
        Link link = event.getLink();
        if (event.getLink().getName().equals(tag))
        {
            Target t = new Target();
            t.setAddress(endpoint);
            Source source = new Source();
            source.setAddress(endpoint);
            link.setTarget(t);
            link.setSource(source);
        }
    }

    @Override
    public void onLinkRemoteOpen(Event event)
    {
        this.connectionWasOpened = true;
    }

    @Override
    public void onConnectionRemoteClose(Event event)
    {
        // Code_SRS_SERVICE_SDK_JAVA_AMQPSENDHANDLER_34_032: [This function shall close the transport tail]
        event.getTransport().close_tail();
    }

    /**
     * If an exception was encountered while opening the AMQP connection, this function shall throw that saved exception
     * @throws IOException if an exception was encountered while openinging the AMQP connection. The encountered
     * exception will be the inner exception
     */
    protected void validateConnectionWasSuccessful() throws IOException, IotHubException
    {
        if (this.savedException != null)
        {
            throw new IOException("Connection failed to be established", this.savedException);
        }

        if (!this.connectionWasOpened)
        {
            throw new IOException("Connection failed to open");
        }

        if (this.protonJExceptionParser != null)
        {
            throw new IOException("Encountered exception during amqp connection: " + protonJExceptionParser.getError() + " with description " + protonJExceptionParser.getErrorDescription());
        }
    }
}
