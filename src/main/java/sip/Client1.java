package sip;

import audio.AudioStream;
import gui.IConnectSipToGUI;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.*;

/**
 * This class is a UAC template.
 *
 * @author M. Ranganathan
 */

public class Client1 implements SipListener {

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
    private String myIP;
    private int mySipPort;
    private String server = "127.0.0.1:5080";

    private int rtpPort;
    private RTPHandler rtpHandler;
    private static AudioStream audio = null;
    private static byte[] buf = new byte[1024];
    private javax.swing.Timer rtpTimer;

    class ByeTask extends TimerTask {
        Request byeRequest;

        public ByeTask(Request byeRequest) {
            this.byeRequest = byeRequest;
        }

        public void run() {
            try {
                createClientTransaction(byeRequest);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static final String usageString = "java "
            + "examples.myFullNetwork.sip.Client1 \n"
            + ">>>> is your class path set to the root?";

    private static void usage() {
        System.out.println(usageString);
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransaction = requestReceivedEvent.getServerTransaction();

        System.out.println("\n\nRequest " + request.getMethod() + " received at " + sipStack.getStackName()
                + " with server transaction id " + serverTransaction);
        System.out.println(request.getHeader(FromHeader.NAME) + "" + request.getHeader(ToHeader.NAME) +
                request.getHeader(CallIdHeader.NAME));

        if (request.getMethod().equals(Request.BYE))
            processBye(requestReceivedEvent);
        else if (request.getMethod().equals(Request.INVITE))
            iConnectSipToGUI.handleCallInvitation(requestReceivedEvent, serverTransaction);
        else {
            try {
                serverTransaction.sendResponse(messageFactory.createResponse(Response.ACCEPTED, request));
            } catch (SipException | ParseException | InvalidArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    public void responseToInvite(RequestEvent requestReceivedEvent, ServerTransaction serverTransaction) {
        try {
            Request request = requestReceivedEvent.getRequest();
            Response response;
            if (!isInConversation) {
                response = messageFactory.createResponse(Response.RINGING, request);
                serverTransaction.sendResponse(response);
            }

            response = messageFactory.createResponse(Response.OK, request);

            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("54321");

            if (request.getMethod().equals(Request.INVITE)) {
                Address address = addressFactory.createAddress("Server <sip:" + myIP
                        + ":" + mySipPort + ">");
                ContactHeader contactHeader = headerFactory.createContactHeader(address);
                response.addHeader(contactHeader);
            }

            addContent(response, rtpPort);

            serverTransaction.sendResponse(response);
            isInConversation = true;
        } catch (Exception e) {
            printException(e);
        }
    }

    public void processBye(RequestEvent requestEvent) {
        try {
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            System.out.println("got a bye .");
            if (serverTransaction == null) {
                System.out.println("null TransactionID.");
                sipProvider.sendResponse(messageFactory.createResponse(Response.OK, requestEvent.getRequest()));
                return;
            }
            Dialog dialog = serverTransaction.getDialog();
            System.out.println("Dialog State = " + dialog.getState());
            serverTransaction.sendResponse(messageFactory.createResponse(Response.OK, requestEvent.getRequest()));
            System.out.println("Sending OK.");
            System.out.println("Dialog State = " + dialog.getState());

        } catch (Exception e) {
            printException(e);
        }
    }

    // Save the created ACK request, to respond to retransmitted 2xx
    private Request ackRequest;

    public void processResponse(ResponseEvent responseReceivedEvent) {
        Response response = (Response) responseReceivedEvent.getResponse();
        ClientTransaction clientTransaction = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        printResponse(response, cseq);

        if (response.getStatusCode() == Response.OK) {
            if (cseq.getMethod().equals(Request.INVITE)) {
                openRTP(response);
                sendACK(response, clientTransaction);
//                prepareToBye();
            } else if (cseq.getMethod().equals(Request.CANCEL)) {
                handleCancel();
            } else if (cseq.getMethod().equals(Request.REGISTER)) {
//                prepareToCall();
            }
            else if (cseq.getMethod().equals(Request.BYE)){
                rtpTimer.stop();
                rtpHandler.getSender().close();
                audio.closeMic();
            }
        }
    }

    private void printResponse(Response response, CSeqHeader cseq) {
        System.out.println("Got a response");
        System.out.println("Response received : Status Code = " + response.getStatusCode() + " " + cseq);
        System.out.println(response.getHeader(FromHeader.NAME) + "" + response.getHeader(ToHeader.NAME) +
                response.getHeader(CallIdHeader.NAME));
    }

    private void sendACK(Response response, ClientTransaction clientTransaction) {
        System.out.println("Dialog after 200 OK  " + dialog);
        System.out.println("Dialog State after 200 OK  " + dialog.getState());
        System.out.println("Sending ACK for dialog:" + dialog.getDialogId());
        try {
            ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
            addContent(ackRequest, rtpPort);
            dialog.sendAck(ackRequest);
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (SipException e) {
            e.printStackTrace();
        }
        System.out.println("transaction state is " + clientTransaction.getState());
    }

    private void openRTP(Response response) {
        rtpHandler = new RTPHandler(server.split(":")[0], Utils.extractPortFromSdp(response.getContent()), rtpPort, false);
        System.out.println("sending to server on " + Utils.extractPortFromSdp(response.getContent()) + " and receiving from server on " + rtpPort);

        try {
            audio = new AudioStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
        rtpTimer = new javax.swing.Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //get next frame to send from the video, as well as its size
                int audioLength = 0;
                try {
                    audioLength = audio.getnextframe(buf);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                rtpHandler.getSender().send(buf, audioLength);
            }
        });
        rtpTimer.start();
    }

    private void prepareToBye() {
        System.out.println("Type y to send bye");
        Scanner scanner = new Scanner(System.in);
        String byeNow = scanner.nextLine();
        if (byeNow.equals("y"))
            if (Proxy.callerSendsBye && !byeTaskRunning) {
                byeTaskRunning = true;
                rtpTimer.stop();
                rtpHandler.getReceiver().close();
                Request byeRequest = null;
                try {
                    byeRequest = dialog.createRequest(Request.BYE);
                } catch (SipException e) {
                    e.printStackTrace();
                }
                new Timer().schedule(new ByeTask(byeRequest), 4000);
            }
    }

    public void sendBye(){
        if (Proxy.callerSendsBye && !byeTaskRunning) {
            byeTaskRunning = true;
            rtpHandler.getReceiver().close();
            Request byeRequest = null;
            try {
                byeRequest = dialog.createRequest(Request.BYE);
                addContent(byeRequest, rtpPort);
            } catch (SipException e) {
                e.printStackTrace();
            }
            new Timer().schedule(new ByeTask(byeRequest), 4000);
        }
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

    private void prepareToCall() {
        System.out.println("Type \"y\" to call client2");
        Scanner scanner = new Scanner(System.in);
        String callNow = scanner.nextLine();
        if (callNow.equals("y"))
            invite("Client2NameDisplay");
    }

    public void invite(String callee) {
        Request request = createRequset(Request.INVITE, "Client1Name",
                "Client1NameDisplay", "port5060.com",
                "user", callee, "domain", "127.0.0.1:5080");
        createClientTransaction(request);
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

        this.myIP = myIP;
        this.mySipPort = mySipPort;
        this.rtpPort = Utils.getRandomPort();

        initFactories();

        initStack(serverAddress);

        initSipProvider();

        this.iConnectSipToGUI = iConnectSipToGUI;

//        register("Client1Name", "Client1NameDisplay", "port5060.com",
//                "ServerName", null, "port5080.com");
    }

    private void initSipProvider() {
        try {
            udpListeningPoint = sipStack.createListeningPoint(myIP, mySipPort, transport);
            System.out.println("listeningPoint = " + udpListeningPoint);
            sipProvider = sipStack.createSipProvider(udpListeningPoint);
            System.out.println("SipProvider = " + sipProvider);
            sipProvider.addSipListener(this);
        } catch (Exception e) {
            printException(e);
        }
    }

    private void initStack(String serverAddress) {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.OUTBOUND_PROXY", serverAddress + "/"
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

    public void register(String fromUsername, String fromDisplay, String fromDomain,
                         String toUsername, String toDisplay, String toDomain, String serverAddress) {
        Request request = createRequset(Request.REGISTER, fromUsername, fromDisplay,
                fromDomain, toUsername, toDisplay, toDomain, serverAddress);
        createClientTransaction(request);
    }

    private void createClientTransaction(Request request) {
        try {
            // Create the client transaction.
            inviteTid = sipProvider.getNewClientTransaction(request);
            System.out.println("inviteTid = " + inviteTid);

            // send the request out.
            if (dialog != null)
                dialog.sendRequest(inviteTid);
            else inviteTid.sendRequest();
            System.out.println();
            System.out.println("Sending Request:" + request.getMethod());
            System.out.println(request.getHeader(FromHeader.NAME) + "" + request.getHeader(ToHeader.NAME));

            dialog = inviteTid.getDialog();
        } catch (Exception e) {
            printException(e);
        }
    }

    private Request createRequset(
            String method, String fromUsername, String fromDisplay, String fromDomain,
            String toUsername, String toDisplay, String toDomain, String serverAddress) {

        Request request = null;

        try {
            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(fromUsername, fromDomain);
            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplay);
            FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = addressFactory.createSipURI(toUsername, toDomain);
            Address toNameAddress = addressFactory.createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplay);
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

            // create Request URI
            SipURI requestURI = addressFactory.createSipURI("Server", serverAddress);

            // Create ViaHeaders
            ArrayList viaHeaders = new ArrayList();
            ViaHeader viaHeader = headerFactory.createViaHeader(udpListeningPoint.getIPAddress(), udpListeningPoint.getPort(), transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, method);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            // Create the request.
            request = messageFactory.createRequest(requestURI, method, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromUsername, udpListeningPoint.getIPAddress());
            contactURI.setPort(udpListeningPoint.getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromDisplay);

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            addContent(request, rtpPort);

            Header callInfoHeader = headerFactory.createHeader("Call-Info", "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);
        } catch (Exception e) {
            printException(e);
        }

        return request;
    }

    public void addContent(Message message, int port) {
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

    public static void main(String args[]) {
        new Client1().init("127.0.0.1", 5060, "127.0.0.1:5080", null);
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
        System.out.println("dialogTerminatedEvent" + dialogTerminatedEvent.getDialog().getDialogId());
    }
}
