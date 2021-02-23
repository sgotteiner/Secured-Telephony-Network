package sip;

import gui.IConnectSipToGUI;
import rtp.RTPHandler;
import rtp.RTPpacket;
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
import javax.sound.sampled.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.text.ParseException;
import java.util.*;

/**
 * This class is a UAC template.
 *
 * @author M. Ranganathan
 */

public class Client2 implements SipListener {

    private IConnectSipToGUI iConnectSipToGUI;

    private static SipProvider sipProvider;

    SipFactory sipFactory = null;

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static SipStack sipStack = null;

    private ContactHeader contactHeader;

    private ListeningPoint udpListeningPoint;

    private ClientTransaction inviteTid;

    private Dialog dialog;

    private boolean byeTaskRunning, isInConversation = false;

    // If you want to try TCP transport change the following to
    private String transport = "udp";
    private String myPort = "127.0.0.1:5061";
    private String peerHostPort = "127.0.0.1:5080";

    private int rtpPort;
    private RTPHandler rtpHandler;
    private static byte[] buf = new byte[1024];
    private DatagramPacket datagramPacket;
    private static SourceDataLine speaker;
    private javax.swing.Timer rtpTimer;

    class ByeTask extends TimerTask {
        Dialog dialog;

        public ByeTask(Dialog dialog) {
            this.dialog = dialog;
        }

        public void run() {
            try {
                Request byeRequest = this.dialog.createRequest(Request.BYE);
                ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(ct);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransaction = requestReceivedEvent.getServerTransaction();
        if (serverTransaction == null) {
            try {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            } catch (TransactionAlreadyExistsException e) {
                e.printStackTrace();
            } catch (TransactionUnavailableException e) {
                e.printStackTrace();
            }
        }

        printRequest(request, serverTransaction);

        if (request.getMethod().equals(Request.BYE))
            processBye(requestReceivedEvent);
        else if (request.getMethod().equals(Request.INVITE)) {
//            responseToInvite(requestReceivedEvent, serverTransaction);
            iConnectSipToGUI.handleCallInvitation(requestReceivedEvent, serverTransaction);
        } else if (request.getMethod().equals(Request.ACK)) {
            openRTP(Utils.extractPortFromSdp(request.getContent()));
        }
    }

    private void printRequest(Request request, ServerTransaction serverTransaction) {
        System.out.println("\n\nRequest " + request.getMethod() + " received at " + sipStack.getStackName() +
                " with server transaction id " + serverTransaction);
        System.out.println(request.getHeader(FromHeader.NAME) + "" + request.getHeader(ToHeader.NAME) +
                request.getHeader(CallIdHeader.NAME));
    }

    public void responseToInvite(RequestEvent requestReceivedEvent, ServerTransaction serverTransaction) {
        try {
            Request request = requestReceivedEvent.getRequest();
            Response response;
            if (!isInConversation) {
                response = messageFactory.createResponse(Response.RINGING, request);
                serverTransaction.sendResponse(response);
            }
//            System.out.println("Type \"y\" to answer " +
//                    ((FromHeader) request.getHeader(FromHeader.NAME)).getAddress().getDisplayName());
//            Scanner scanner = new Scanner(System.in);
//            String answer = scanner.nextLine();
//            if (answer.equals("y")) {

                response = messageFactory.createResponse(Response.OK, request);

                ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("54321");

                if (request.getMethod().equals(Request.INVITE)) {
                    SipURI contactURI = addressFactory.createSipURI("Client2Name", udpListeningPoint.getIPAddress());
                    contactURI.setPort(udpListeningPoint.getPort());

                    Address contactAddress = addressFactory.createAddress(contactURI);

                    // Add the contact address.
                    contactAddress.setDisplayName("Client2Display");
//                    Address address = addressFactory.createAddress("sip.Client2 <sip:" + myPort.split(":")[0]
//                            + ":" + myPort.split(":")[1] + ">");
                    ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
                    response.addHeader(contactHeader);
                }

                Utils.addContent(headerFactory, response, rtpPort);

                serverTransaction.sendResponse(response);
                isInConversation = true;
//            }
        } catch (Exception e) {
            printException(e);
        }
    }

    private void openRTP(int port) {
        rtpHandler = new RTPHandler("127.0.0.1", port, rtpPort, false);
        System.out.println("sending to server on " + port + " and receiving from server on " + rtpPort);

        datagramPacket = new DatagramPacket(buf, buf.length);
        AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
        try {
            speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            speaker.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        speaker.start();

        rtpTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                rtpHandler.getSender().send(buf, buf.length); //send nothing just to see that this is working

                rtpHandler.getReceiver().receive(datagramPacket);

                //create an rtp.RTPpacket object from the DP
                RTPpacket rtpPacket = new RTPpacket(datagramPacket.getData(), datagramPacket.getLength());
                int seqNumber = rtpPacket.getsequencenumber();
                //this is the highest seq num received

                //print important header fields of the RTP packet received:
                System.out.println("Got RTP packet with SeqNum # " + seqNumber
                        + " TimeStamp " + rtpPacket.gettimestamp() + " ms, of type "
                        + rtpPacket.getpayloadtype());

                //print header bitstream:
                rtpPacket.printheader();

                //get the payload bitstream from the rtp.RTPpacket object
                int payloadLength = rtpPacket.getpayload_length();
                byte[] payload = new byte[payloadLength];
                rtpPacket.getpayload(payload);

                speaker.write(payload, 0, payloadLength);
            }
        });
        rtpTimer.start();
    }

    public void processBye(RequestEvent requestEvent) {
        isInConversation = false;
        closeRTP();
        responseToBye(requestEvent);
    }

    private void responseToBye(RequestEvent requestEvent) {
        try {
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            System.out.println("got a bye .");
            Response response = messageFactory.createResponse(Response.OK, requestEvent.getRequest());
            if (serverTransaction == null) {
                System.out.println("null TransactionID.");
                sipProvider.sendResponse(response);
                return;
            }
            Dialog dialog = serverTransaction.getDialog();
            System.out.println("Dialog State = " + dialog.getState());
            Utils.addContent(headerFactory, response, rtpPort);
            serverTransaction.sendResponse(response);
            System.out.println("Client:  Sending OK.");
            System.out.println("Dialog State = " + dialog.getState());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void closeRTP() {
        rtpTimer.stop();
        rtpHandler.closeAll();
        speaker.drain();
        speaker.close();
    }

    public void sendBye(){
        if (Proxy.callerSendsBye && !byeTaskRunning) {
            byeTaskRunning = true;
            rtpTimer.stop();
//            rtpHandler.close("", System.currentTimeMillis());
            Request byeRequest = null;
            try {
                byeRequest = dialog.createRequest(Request.BYE);
            } catch (SipException e) {
                e.printStackTrace();
            }
            Utils.addContent(headerFactory, byeRequest, rtpPort);
//            new java.util.Timer().schedule(new Client2.ByeTask(byeRequest), 4000);
        }
    }

    // Save the created ACK request, to respond to retransmitted 2xx
    private Request ackRequest;

    public void processResponse(ResponseEvent responseReceivedEvent) {
        Response response = (Response) responseReceivedEvent.getResponse();
        ClientTransaction clientTransaction = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        printResponse(response, cseq);

        try {
            if (response.getStatusCode() == Response.OK) {
                if (cseq.getMethod().equals(Request.INVITE)) {
                    sendACK(response, clientTransaction);
                } else if (cseq.getMethod().equals(Request.CANCEL)) {
                    handleCancel();
                } else if (cseq.getMethod().equals(Request.BYE)) {
                    System.out.println("Got a BYE");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void printResponse(Response response, CSeqHeader cseq) {
        System.out.println("Got a response");
        System.out.println("Response received : Status Code = " + response.getStatusCode() + " " + cseq);
        System.out.println(response.getHeader(FromHeader.NAME) + "" + response.getHeader(ToHeader.NAME) +
                response.getHeader(CallIdHeader.NAME));
    }

    private void sendACK(Response response, ClientTransaction tid) {
        System.out.println("Dialog after 200 OK  " + dialog);
        System.out.println("Dialog State after 200 OK  " + dialog.getState());
        System.out.println("Sending ACK for dialog:" + dialog.getDialogId());
        try {
            ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
            Utils.addContent(headerFactory, ackRequest, rtpPort);
            dialog.sendAck(ackRequest);
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (SipException e) {
            e.printStackTrace();
        }
        System.out.println("transaction state is " + tid.getState());
    }

    private void handleCancel() {
        if (dialog.getState() == DialogState.CONFIRMED) {
            // oops cancel went in too late. Need to hang up the
            System.out.println("Sending BYE -- cancel went in too late !!");
            try {
                Request byeRequest = dialog.createRequest(Request.BYE);
                ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(ct);
            } catch (SipException e) {
                e.printStackTrace();
            }

        }
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {

        System.out.println("Transaction Time out");
    }

    public void sendCancel() {
        try {
            System.out.println("Sending cancel");
            Request cancelRequest = inviteTid.createCancel();
            ClientTransaction cancelTid = sipProvider.getNewClientTransaction(cancelRequest);
            cancelTid.sendRequest();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void init(String myIP, int mySipPort, String serverAddress, IConnectSipToGUI iConnectSipToGUI) {

        this.iConnectSipToGUI = iConnectSipToGUI;
        this.rtpPort = Utils.getRandomPort();

        initFactories();

        initStack();

        initSipProvider();

        register();
    }

    private void initSipProvider() {
        try {
            udpListeningPoint = sipStack.createListeningPoint(myPort.split(":")[0], Integer.valueOf(myPort.split(":")[1]), transport);
            System.out.println("listeningPoint = " + udpListeningPoint);
            sipProvider = sipStack.createSipProvider(udpListeningPoint);
            System.out.println("SipProvider = " + sipProvider);
            sipProvider.addSipListener(this);
        } catch (Exception e) {
            printException(e);
        }

    }

    private void initStack() {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort + "/"
                + transport);
        // If you want to use UDP then uncomment this.
        properties.setProperty("javax.sip.STACK_NAME", "client");

        // The following properties are specific to nist-sip
        // and are not necessarily part of any other jain-sip
        // implementation.
        // You can set a max message size for tcp transport to
        // guard against denial of service attack.
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "shootistdebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "shootistlog.txt");

        // Drop the client connection after we are done with the transaction.
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                "false");
        // Set to 0 (or NONE) in your production code for max speed.
        // You need 16 (or TRACE) for logging traces. 32 (or DEBUG) for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "DEBUG");

        try {
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
            System.out.println("createSipStack " + sipStack);
        } catch (PeerUnavailableException e) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            printException(e);
        }
    }

    private void initFactories() {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
        } catch (Exception e) {
            printException(e);
        }
    }

    public void register() {
        Request request = createRequset(Request.REGISTER);
        createClientTransaction(request);
    }

    public void invite(){
        Request request = createRequset(Request.INVITE);
        createClientTransaction(request);
    }

    private void createClientTransaction(Request request) {
        try {
            // Create the client transaction.
            inviteTid = sipProvider.getNewClientTransaction(request);
            System.out.println("inviteTid = " + inviteTid);

            // send the request out.
            inviteTid.sendRequest();

            dialog = inviteTid.getDialog();
        } catch (Exception e) {
            printException(e);
        }
    }

    private Request createRequset(String method) {
        Request request = null;

        String fromName = "Client2Name";
        String fromSipAddress = "port5061.com";

        String toSipAddress, toName;
        if (method.equals(Request.REGISTER)) {
            toSipAddress = "port5080.com";
            toName = "ServerName";
        } else {
            toSipAddress = "port5060.com";
            toName = "Client1Name";
        }

        try {
            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(fromName, fromSipAddress);
            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromName + "Display");
            FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = addressFactory.createSipURI(toName, toSipAddress);
            Address toNameAddress = addressFactory.createAddress(toAddress);
            toNameAddress.setDisplayName(toName + "Display");
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

            // create Request URI
            SipURI requestURI = addressFactory.createSipURI(toName, peerHostPort);

            // Create ViaHeaders
            ArrayList viaHeaders = new ArrayList();
            ViaHeader viaHeader = headerFactory.createViaHeader(udpListeningPoint.getIPAddress(), udpListeningPoint.getPort(), transport, null);
            //ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress, sipProvider.getListeningPoint(transport).getPort(), transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, method);
            //CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            // Create the request.
            request = messageFactory.createRequest(requestURI, method, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);

            // Create contact headers
            SipURI contactUrl = addressFactory.createSipURI(fromName, udpListeningPoint.getIPAddress());
            contactUrl.setPort(udpListeningPoint.getPort());
            contactUrl.setLrParam();

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromName, udpListeningPoint.getIPAddress());
            contactURI.setPort(sipProvider.getListeningPoint(transport).getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

//            // You can add extension headers of your own making
//            // to the outgoing SIP request.
//            // Add the extension header.
//            Header extensionHeader = headerFactory.createHeader("My-Header",
//                    "my header value");
//            request.addHeader(extensionHeader);

            Utils.addContent(headerFactory, request, rtpPort);

            Header callInfoHeader = headerFactory.createHeader("Call-Info", "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);
        } catch (Exception e) {
            printException(e);
        }

        return request;
    }

    private void printException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getMessage());
//        junit.framework.TestCase.fail("Exit JVM");
    }

    public static void main(String args[]) {
        new Client2().init("localhost", 5061, "localhost",null);
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IOException happened for "
                + exceptionEvent.getHost() + " port = "
                + exceptionEvent.getPort());

    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        System.out.println("Transaction terminated event recieved");
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        System.out.println("dialogTerminatedEvent " + dialogTerminatedEvent.getDialog().getDialogId());
    }
}
