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

    private static SipFactory sipFactory;
    private static AddressFactory addressFactory;
    private static MessageFactory messageFactory;
    private static HeaderFactory headerFactory;

    private static SipStack sipStack;

    private static final String myIP = "127.0.0.1";

    private static final int mySipPort = 5080;

    private final AtomicLong counter = new AtomicLong();

    private ListeningPoint listeningPoint;

    private SipProvider sipProvider;

    private final String transport = "udp";

    public static final boolean callerSendsBye = true;

    private final HashMap<String, SipURI> registrar = new HashMap<String, SipURI>();

    private final HashMap<String, String> rtpClientServer = new HashMap<String, String>();
    private final HashMap<String, String> rtpClients = new HashMap<String, String>();
    private final HashMap<String, String> waitingForAckMap = new HashMap<String, String>();
    private final HashMap<String, RTPHandler> rtpHandlers = new HashMap<String, RTPHandler>();

    //process and forward the request to the relevant clients
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent);
        } else if (request.getMethod().equals(Request.REGISTER)) {
            processRegister(requestEvent);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent);
        }
    }

    //process and forward the responses to the relevant clients
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

                        //connect the rtp ports of the current conversation
                        //the handlers will be created in ack
                        int portServer1 = managePortsAfterInviteOK(st, ct, response);

                        //forward the ok to the inviting client
                        Address address = addressFactory.createAddress("Server <sip:" + myIP + ":" + mySipPort + ">");
                        ContactHeader contactHeader = headerFactory.createContactHeader(address);
                        response.addHeader(contactHeader);
                        ToHeader toHeader = (ToHeader) otherResponse.getHeader(ToHeader.NAME);
                        if (toHeader.getTag() == null)
                            toHeader.setTag(((ToHeader) response.getHeader(ToHeader.NAME)).getTag());
                        otherResponse.addHeader(contactHeader);
                        Utils.addContent(headerFactory, otherResponse, portServer1);
                    } else if (ct.getRequest().getMethod().equals(Request.BYE)) {

                        //stop receiving from client1 and sending to client2
                        int rtpPort = Utils.extractPortFromSdp(response.getContent());
                        String address = myIP + ":" + rtpPort;
                        rtpHandlers.get(address).closeAll();
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

    //start the rtp port connection process and print explanations
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

    //Process the ACK request and forward it to the other leg.
    private void processAck(RequestEvent requestEvent) {
        try {
            System.out.println("server: got an ACK! ");

            String serverPort1 = createHandlers(requestEvent.getRequest().getContent());

            Dialog otherDialog = (Dialog) requestEvent.getServerTransaction().getDialog().getApplicationData();
            Request request = otherDialog.createAck(otherDialog.getLocalSeqNumber());
            Utils.addContent(headerFactory, request, Integer.parseInt(serverPort1.split(":")[1]));
            otherDialog.sendAck(request);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //the handlers contain the rtp sender and receiver
    private String createHandlers(Object sdp) {
        int rtpPort1 = Utils.extractPortFromSdp(sdp);
        String serverPort1 = waitingForAckMap.get(myIP + ":" + rtpPort1);
        //this handler contains the receiver to client2 and sender to client1
        rtpHandlers.put(myIP + ":" + rtpPort1, new RTPHandler(myIP,
                rtpPort1, Integer.parseInt(serverPort1.split(":")[1]), true));
        waitingForAckMap.remove(myIP + ":" + rtpPort1);

        String address2 = rtpClients.get(myIP + ":" + rtpPort1);
        int rtpPort2 = Integer.parseInt(address2.split(":")[1]);
        String serverPort2 = waitingForAckMap.get(address2);
        //this handler contains the receiver to client1 and sender to client2
        rtpHandlers.put(address2, new RTPHandler(myIP,
                rtpPort2, Integer.parseInt(serverPort2.split(":")[1]), true));
        waitingForAckMap.remove(myIP + ":" + rtpPort2);

        System.out.println("handler receives at " + serverPort1 + " from client2 and sends to " + rtpPort1 + " at client1");
        System.out.println("handler receives at " + serverPort2 + " from client1 and sends to " + rtpPort2 + " at client2");

        return serverPort1;
    }

    //process and forward the bye request
    private void processBye(RequestEvent requestEvent) {
        try {
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            Dialog dialog = serverTransaction.getDialog();
            Dialog otherDialog = (Dialog) dialog.getApplicationData();
            Request request = otherDialog.createRequest(Request.BYE); //!= requestEvent.getRequest()

            //the client transaction for requestEvent.getRequest() already exists so it will fail.
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.setApplicationData(serverTransaction);
            serverTransaction.setApplicationData(clientTransaction);
            serverTransaction.getDialog().setApplicationData(clientTransaction.getDialog());
            clientTransaction.getDialog().setApplicationData(dialog);
            otherDialog.sendRequest(clientTransaction);

            //stop receiving from client2 and sending to client1
            int rtpPort = Utils.extractPortFromSdp(requestEvent.getRequest().getContent());
            String address = myIP + ":" + rtpPort;
            rtpHandlers.get(address).closeAll();
            rtpHandlers.remove(address);
        } catch (Exception e) {
            printException(e);
        }
    }

    //check if this client can make this invitation in db and forward it
    private void processInvite(RequestEvent requestEvent) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        ServerTransaction st = requestEvent.getServerTransaction();
        Request request = requestEvent.getRequest();
        if (st == null) {
            try {
                st = sipProvider.getNewServerTransaction(request);
            } catch (TransactionAlreadyExistsException | TransactionUnavailableException e) {
                e.printStackTrace();
            }
        }

        //if not an exception is thrown
        SipURI toUri = checkIfRegistered(st, request);

        String ip = Utils.getSenderIPfromMessage(request);
        //do this client have the permission to make this call
        boolean isCall = PermissionDB.getInstance().isIPgoodForCall(ip, toUri.getHost());
        if (isCall) {
            forwardCall(st, request, toUri);
        }
    }

    //the check uses the registrar HashMap
    private SipURI checkIfRegistered(ServerTransaction st, Request request) {

        //unregistered users can't call and be called
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        SipURI fromUri = registrar.get(from.getAddress().getDisplayName());
        SipURI toUri = registrar.get(((To) to).getDisplayName());
        if (toUri == null || fromUri == null) {
            try {
                st.sendResponse(messageFactory.createResponse(Response.UNAUTHORIZED, request));
            } catch (SipException | InvalidArgumentException | ParseException e) {
                e.printStackTrace();
            }
            System.out.println("User is not registered.");
            throw new RuntimeException("User not registered");
        }
        return toUri;
    }

    //only after permission check
    private void forwardCall(ServerTransaction st, Request request, SipURI toUri) {
        System.out.println("server: got an Invite sending Trying");

        Dialog dialog = st.getDialog();

        try {
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
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //create the client transaction for the call, returns it and sends the request
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

    //register a user to the system if his ip has a permission
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
                Response response = messageFactory.createResponse(Response.OK, request);
                ServerTransaction serverTransaction = sipProvider.getNewServerTransaction(request);
                serverTransaction.sendResponse(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

    //initial the sip factories and stack
    private void init() {

        initFactories();

        initStack();

        initSipProvider();
    }

    private void initSipProvider() {
        try {
            this.listeningPoint = sipStack.createListeningPoint(myIP, mySipPort, transport);
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //in order to create sip objects
    private void initFactories() {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    //the sip stack has the listening points and providers that are used in all the dialogs
    private void initStack() {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "Server");

        try {
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
        } catch (PeerUnavailableException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
            if (e.getCause() != null)
                e.getCause().printStackTrace();
        }
    }

    //start the pbx
    public static void main(String[] args) {
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

    String fromName = "Server";
    //create the request to forward from the received request
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

            Utils.addContent(headerFactory, request, port);
        } catch (Exception e) {
            printException(e);
        }
        return request;
    }

    private void printException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getMessage());
    }
}
