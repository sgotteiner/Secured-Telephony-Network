package sip;

import db.PermissionDB;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.header.To;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import rtp.RTPHandler;
import utils.Utils;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

public class Proxy implements SipListener {

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static SipStack sipStack;

    private static final String myIP = "127.0.0.1";

    private static final int mySipPort = 5080;

    private AtomicLong counter = new AtomicLong();

    private ListeningPoint listeningPoint;

    private SipProvider sipProvider;

    private String transport = "udp";

    public static final boolean callerSendsBye = true;

    private HashMap<String, SipURI> registrar = new HashMap<String, SipURI>();

    private HashMap<String, String> rtpClientServer = new HashMap<String, String>();
    private HashMap<String, String> rtpClients = new HashMap<String, String>();
    private HashMap<String, String> waitingForAckMap = new HashMap<String, String>();
    private HashMap<String, RTPHandler> rtpHandlers = new HashMap<String, RTPHandler>();

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent);
        } else if (request.getMethod().equals(Request.CANCEL)) {
            processCancel(requestEvent);
        } else if (request.getMethod().equals(Request.REGISTER)) {
            processRegister(requestEvent);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent);
        } else {
            //processInDialogRequest(requestEvent);
        }
    }

    public void processResponse(ResponseEvent responseEvent) {
        ClientTransaction ct = responseEvent.getClientTransaction();
        if (ct == null) {
            return;
        }

        Response response = responseEvent.getResponse();
        ServerTransaction st = (ServerTransaction) ct.getApplicationData();

        try {
            if (st != null) {
                Response otherResponse = messageFactory.createResponse(response.getStatusCode(), st.getRequest());
                if (response.getStatusCode() == Response.OK) {
                    if (ct.getRequest().getMethod().equals(Request.INVITE)) {
                        int portServer1 = managePortsAfterInviteOK(st, ct, response);

                        Address address = addressFactory.createAddress("Server <sip:" + myIP + ":" + mySipPort + ">");
                        ContactHeader contactHeader = headerFactory.createContactHeader(address);
                        response.addHeader(contactHeader);
                        ToHeader toHeader = (ToHeader) otherResponse.getHeader(ToHeader.NAME);
                        if (toHeader.getTag() == null)
                            toHeader.setTag(((ToHeader) response.getHeader(ToHeader.NAME)).getTag());
                        otherResponse.addHeader(contactHeader);
                        addContent(otherResponse, portServer1);
                    } else if (ct.getRequest().getMethod().equals(Request.BYE)) {
                        int rtpPort = Utils.extractPortFromSdp(response.getContent());
                        String address = myIP + ":" + rtpPort;
                        rtpHandlers.get(address).closeAll(); //stop receiving from client1 and sending to client2
                        rtpHandlers.remove(rtpClientServer.get(address));
                        rtpClientServer.remove(address);
                        System.out.println(rtpClientServer.size());
                    }
                }
                st.sendResponse(otherResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int managePortsAfterInviteOK(ServerTransaction st, ClientTransaction ct, Response response) {
        int rtpPort1 = Utils.extractPortFromSdp(st.getRequest().getContent());
        String ip1 = Utils.getSenderIPfromMessage(st.getRequest());
        int portServer1 = Utils.extractPortFromSdp(ct.getRequest().getContent());
        int rtpPort2 = Utils.extractPortFromSdp(response.getContent());
        String ip2 = Utils.getSenderIPfromMessage(response);
        int portServer2 = Utils.getRandomPort();
        rtpClients.put(ip1 + ":" + rtpPort1, ip2 + ":" + rtpPort2);
        rtpClientServer.put(ip2 + ":" + rtpPort2, myIP + ":" + portServer2);
        waitingForAckMap.put(ip1 + ":" + rtpPort2, myIP + ":" + portServer1); //rtpPort2 will be got in ack and then an rtp handler will be created
        rtpClientServer.put(ip1 + ":" + rtpPort1, myIP + ":" + portServer1); //the rtp handler will be created after client1 ack
        waitingForAckMap.put(ip1 + ":" + rtpPort1, myIP + ":" + portServer2); //rtpPort1 will be got in ack and then an rtp handler will be created

        System.out.println("client1 sends rtp to server to " + portServer1 + " and the server sends to " + rtpPort1 + " in client1, the handler will be created in ack");
        System.out.println("client2 sends rtp to server to " + portServer2 + " and the server sends to " + rtpPort2 + " in client2");
        System.out.println("after ack of client1 handler that receives at " + portServer2 + " from client2 and sends to " + rtpPort1 + " at client1");
        System.out.println("after ack of client1 handler that receives at " + portServer1 + " from client1 and sends to " + rtpPort2 + " at client2");

        return portServer1;
    }

    /**
     * Process the ACK request, forward it to the other leg.
     */
    private void processAck(RequestEvent requestEvent) {
        try {
            int rtpPort1 = Utils.extractPortFromSdp(requestEvent.getRequest().getContent());
            String serverPort1 = waitingForAckMap.get(myIP + ":" + rtpPort1);
            String sendIP = rtpClientServer.get(serverPort1);
            rtpHandlers.put(myIP + ":" + rtpPort1, new RTPHandler(myIP,
                    rtpPort1, Integer.parseInt(serverPort1.split(":")[1]), true));
            waitingForAckMap.remove(myIP + ":" + rtpPort1);
            String address2 = rtpClients.get(myIP + ":" + rtpPort1);
            int rtpPort2 = Integer.parseInt(address2.split(":")[1]);
            String serverPort2 = waitingForAckMap.get(address2);
            rtpHandlers.put(address2, new RTPHandler(myIP,
                    rtpPort2, Integer.parseInt(serverPort2.split(":")[1]), true));
            waitingForAckMap.remove(myIP + ":" + rtpPort2);
            Dialog dialog = requestEvent.getServerTransaction().getDialog();
            System.out.println("server: got an ACK! ");
            System.out.println("handler receives at " + serverPort1 + " from client2 and sends to " + rtpPort1 + " at client1");
            System.out.println("handler receives at " + serverPort2 + " from client1 and sends to " + rtpPort2 + " at client2");
            System.out.println("Dialog State = " + dialog.getState());
            Dialog otherDialog = (Dialog) dialog.getApplicationData();
            Request request = otherDialog.createAck(otherDialog.getLocalSeqNumber());
            addContent(request, Integer.parseInt(serverPort1.split(":")[1]));
            otherDialog.sendAck(request);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void processBye(RequestEvent requestEvent) {
        try {
            ServerTransaction serverTransaction = (ServerTransaction) requestEvent.getServerTransaction();
            Dialog dialog = serverTransaction.getDialog();
            Dialog otherDialog = (Dialog) dialog.getApplicationData();
            Request request = otherDialog.createRequest(Request.BYE); //!= requestEvent.getRequest()
            addContent(request, Utils.extractPortFromSdp(requestEvent.getRequest().getContent()));
            //the client transaction for requestEvent.getRequest() already exists so it will fail.
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.setApplicationData(serverTransaction);
            serverTransaction.setApplicationData(clientTransaction);
            serverTransaction.getDialog().setApplicationData(clientTransaction.getDialog());
            clientTransaction.getDialog().setApplicationData(dialog);
            otherDialog.sendRequest(clientTransaction);
            int rtpPort = Utils.extractPortFromSdp(requestEvent.getRequest().getContent());
            String address = "127.0.0.1" + ":" + rtpPort;
            rtpHandlers.get(address).closeAll(); //stop receiving from client2 and sending to client1
            rtpHandlers.remove(address);
        } catch (Exception e) {
            printException(e);
        }
    }

    /**
     * Process the invite request.
     */
    private void processInvite(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        String ip = Utils.getSenderIPfromMessage(request);
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        SipURI toUri = registrar.get(((To) to).getDisplayName());
        boolean isCall = PermissionDB.getInstance().isIPgoodForCall(ip, toUri.getHost());
        if (isCall) {
            try {
                System.out.println("server: got an Invite sending Trying");
                ServerTransaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    st = sipProvider.getNewServerTransaction(request);
                }
                Dialog dialog = st.getDialog();

                if (toUri == null) {
                    System.out.println("User " + toUri + " is not registered.");
                    throw new RuntimeException("User not registered " + toUri);
                } else {
                    String displayName = ((To) request.getHeader(ToHeader.NAME)).getDisplayName();
                    Address toNameAddress = addressFactory.createAddress(toUri);
                    toNameAddress.setDisplayName(displayName);
                    ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);
                    request.removeHeader(ToHeader.NAME);
                    request.setHeader(toHeader);
                    ClientTransaction otherLeg = call(request);
                    otherLeg.setApplicationData(st);
                    st.setApplicationData(otherLeg);
                    dialog.setApplicationData(otherLeg.getDialog());
                    otherLeg.getDialog().setApplicationData(dialog);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Process the any in dialog request - MESSAGE, BYE, INFO, UPDATE.
     */
    private void processInDialogRequest(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction();
        Dialog dialog = requestEvent.getDialog();
        System.out.println("local party = " + dialog.getLocalParty());
        try {
            System.out.println("Server:  got a bye sending OK.");
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            System.out.println("Dialog State is " + serverTransactionId.getDialog().getState());

            Dialog otherLeg = (Dialog) dialog.getApplicationData();
            Request otherBye = otherLeg.createRequest(request.getMethod());
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(otherBye);
            clientTransaction.setApplicationData(serverTransactionId);
            serverTransactionId.setApplicationData(clientTransaction);
            otherLeg.sendRequest(clientTransaction);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void processRegister(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String ip = Utils.getSenderIPfromMessage(request);
        boolean isRegister = PermissionDB.getInstance().isIPgoodForRegistration(ip);
        if (isRegister) {
            PermissionDB.getInstance().addUserCallPermission(ip);
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            SipURI contactUri = (SipURI) contact.getAddress().getURI();
            FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
            SipURI fromUri = (SipURI) from.getAddress().getURI();
            registrar.put(((From) from).getDisplayName(), contactUri);
            try {
                Response response = this.messageFactory.createResponse(Response.OK, request);
                ServerTransaction serverTransaction = sipProvider.getNewServerTransaction(request);
                serverTransaction.sendResponse(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processCancel(RequestEvent requestEvent) {
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        System.out.println("state = " + transaction.getState());
        System.out.println("dialog = " + transaction.getDialog());
        System.out.println("dialogState = "
                + transaction.getDialog().getState());
        System.out.println("Transaction Time out");
    }

    private void init() {

        ConsoleAppender console = new ConsoleAppender(); //create appender
        //configure the appender
        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(PATTERN));
        console.setThreshold(Level.DEBUG);
        console.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(console);
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "Server");
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
//        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "LOG4J");
//        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
//                "shootmedebug.txt");
//        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
//                "shootmelog.txt");
//        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getName());

        try {
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
            System.out.println("sipStack = " + sipStack);
        } catch (PeerUnavailableException e) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            e.printStackTrace();
            System.err.println(e.getMessage());
            if (e.getCause() != null)
                e.getCause().printStackTrace();
//            junit.framework.TestCase.fail("Exit JVM");
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            this.listeningPoint = sipStack.createListeningPoint(myIP, mySipPort, transport);

            Proxy listener = this;

            sipProvider = sipStack.createSipProvider(listeningPoint);
            System.out.println("udp provider " + sipProvider);
            sipProvider.addSipListener(listener);

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void main(String args[]) {
        new Proxy().init();
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IOException");
    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        if (transactionTerminatedEvent.isServerTransaction())
            System.out.println("Transaction terminated event recieved" + transactionTerminatedEvent.getServerTransaction());
        else
            System.out.println("Transaction terminated " + transactionTerminatedEvent.getClientTransaction());
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        System.out.println("Dialog terminated event recieved");
        Dialog d = dialogTerminatedEvent.getDialog();
        System.out.println("Local Party = " + d.getLocalParty());
    }

    private ClientTransaction call(Request request) {
        try {
            request = createRequest(request, Utils.getRandomPort());
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();
            return clientTransaction;
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    String fromName = "Server";

    private Request createRequest(Request request, int port) {
        //Request request = null;
        try {
            // Create ViaHeaders
            ArrayList viaHeaders = new ArrayList();
            ViaHeader viaHeader = headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                    listeningPoint.getPort(), transport, null);
            // add via headers
            viaHeaders.add(viaHeader);

            FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
            CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
            CSeqHeader cSeqHeader = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
            MaxForwardsHeader maxForwardsHeader = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);

            // Create the request.
            request = messageFactory.createRequest(registrar.get(((To) toHeader).getDisplayName()),
                    cSeqHeader.getMethod(), callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
            // Create contact headers
            String host = "127.0.0.1";

            SipURI contactUrl = addressFactory.createSipURI(fromName, host);
            contactUrl.setPort(listeningPoint.getPort());
            contactUrl.setLrParam();

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromName, host);
            contactURI.setPort(listeningPoint.getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            addContent(request, port);
        } catch (Exception e) {
            printException(e);
        }
        return request;
    }

    private void addContent(Message message, int port) {
        ContentTypeHeader contentTypeHeader = null;
        try {
            contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

            String sdpData = "v=0\r\n"
                    + "o=4855 13760799956958020 13760799956958020"
                    + " IN IP4 127.0.0.1\r\n" + "s=mysession session\r\n"
                    + "p=+46 8 52018010\r\n" + "c=IN IP4 127.0.0.1\r\n"
                    + "t=0 0\r\n" + "m=audio " + port + " RTP/AVP 0 4 18\r\n"
                    + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                    + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";

            byte[] contents = sdpData.getBytes();

            message.setContent(contents, contentTypeHeader);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void printException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getMessage());
    }
}
