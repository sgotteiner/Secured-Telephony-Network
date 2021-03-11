package sip;

import audio.AudioStream;
import gui.IConnectSipToGUI;
import rtp.RTPHandler;
import rtp.RTPpacket;
import utils.Utils;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.sound.sampled.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class Client implements SipListener {

    private IConnectSipToGUI iConnectSipToGUI;

    private static SipProvider sipProvider;

    SipFactory sipFactory = null;

    private static AddressFactory addressFactory;

    private static MessageFactory messageFactory;

    private static HeaderFactory headerFactory;

    private static SipStack sipStack = null;

    private ContactHeader contactHeader;

    private ListeningPoint udpListeningPoint;

    private Dialog dialog;

    private boolean byeTaskRunning, isInConversation = false;

    private String transport = "udp";
    private String myIP;
    private int mySipPort;
    private ClientDetails clientDetails;
    private String server = "127.0.0.1:5080";

    private int rtpPort;
    private RTPHandler rtpHandler;
    private static AudioStream audio = null;
    private static byte[] buf = new byte[1024];
    private DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
    private static SourceDataLine speaker;
    private javax.swing.Timer rtpSenderTimer, rtpReceiverTimer;

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

    private Timer byeTimer;

    private boolean isSender, isReceiver;

    public Client() {
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
            iConnectSipToGUI.handleCallInvitation(requestReceivedEvent, serverTransaction);
        } else if (request.getMethod().equals(Request.ACK)) {
            //in order to avoid duplicate sending and receiving the caller is the sender and callee is a receiver
            isSender = false;
            isReceiver = true;
            openRTPWhenCalled();
        }
    }

    private void openRTPWhenCalled() {
        //client1 has started sending after the ok was sent so receiving can be done without getting ReceiveTimeout exception
        AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
        try {
            speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            speaker.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        speaker.start();

        rtpReceiverTimer = new javax.swing.Timer(100, receiverActionListener);
        rtpReceiverTimer.start();
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

            //client2 starts sending for now. When client1 gets the OK he will start sending and receiving.
            //In the ACK client2 will start Receiving
            rtpHandler = new RTPHandler(clientDetails.getMyIP(), Utils.extractPortFromSdp(request.getContent()), rtpPort, false);
            rtpSenderTimer = new javax.swing.Timer(100, notSenderActionListener);
            rtpSenderTimer.start();

            Response response;

            if (!isInConversation) {
                response = messageFactory.createResponse(Response.RINGING, request);
                serverTransaction.sendResponse(response);
            }

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

            dialog = serverTransaction.getDialog();

            isInConversation = true;
        } catch (Exception e) {
            printException(e);
        }
    }

    public void processBye(RequestEvent requestEvent) {
        closeRTP();
        responseToBye(requestEvent);
    }

    private void closeRTP() {
        rtpReceiverTimer.stop();
        rtpSenderTimer.stop();
        rtpHandler.closeAll();
        if (isReceiver) {
            speaker.drain();
            speaker.close();
        }
        if (isSender) {
            audio.closeMic();
        }
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

    public void processResponse(ResponseEvent responseReceivedEvent) {
        Response response = (Response) responseReceivedEvent.getResponse();
        ClientTransaction clientTransaction = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        printResponse(response, cseq);

        if (response.getStatusCode() == Response.OK) {
            switch (cseq.getMethod()) {
                case Request.INVITE:
                    //in order to avoid duplicate sending and receiving the caller is the sender and callee is a receiver
                    isSender = true;
                    isReceiver = false;
                    //other client wants to talk
                    openRTPWhenCall(Utils.extractPortFromSdp(response.getContent()));
                    //the right sip flow
                    sendACK(response, clientTransaction);
                    break;
                case Request.REGISTER:
                    System.out.println("Register successful");
                    break;
                case Request.BYE:
                    //stop sending bye
                    byeTimer.cancel();
                    byeTaskRunning = false;

                    //stop sending audio from microphone
                    rtpSenderTimer.stop();
                    rtpHandler.getSender().close();
                    if (isSender)
                        audio.closeMic();
                    break;
            }
        }
    }

    private void printResponse(Response response, CSeqHeader cseq) {
        System.out.println("Got a response");
        System.out.println("Response received : Status Code = " + response.getStatusCode() + " " + cseq);
        System.out.println(response.getHeader(FromHeader.NAME) + "" + response.getHeader(ToHeader.NAME) +
                response.getHeader(CallIdHeader.NAME));
    }

    //acknowledge the callee's ok
    private void sendACK(Response response, ClientTransaction clientTransaction) {
        System.out.println("Dialog after 200 OK  " + dialog);
        System.out.println("Dialog State after 200 OK  " + dialog.getState());
        System.out.println("Sending ACK for dialog:" + dialog.getDialogId());
        try {
            Request ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
            Utils.addContent(headerFactory, ackRequest, rtpPort);
            dialog.sendAck(ackRequest);
        } catch (InvalidArgumentException | SipException e) {
            e.printStackTrace();
        }
        System.out.println("transaction state is " + clientTransaction.getState());
    }

    private void openRTPWhenCall(int port) {
        //client2 has started sending so the receiver can be opened without getting receiveTimeout exception
        //after ack cient2 will start receiving
        rtpHandler = new RTPHandler(server.split(":")[0], port, rtpPort, false);
        System.out.println("sending to server on " + port + " and receiving from server on " + rtpPort);

        try {
            audio = new AudioStream();
        } catch (Exception e) {
            e.printStackTrace();
        }

        rtpReceiverTimer = new javax.swing.Timer(100, notReceiverActionListener);
        rtpReceiverTimer.start();

        rtpSenderTimer = new javax.swing.Timer(100, senderActionListener);
        rtpSenderTimer.start();
    }

    private ActionListener senderActionListener = new ActionListener() {
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
    };

    private ActionListener notSenderActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            //send nothing just to see that this is working
            rtpHandler.getSender().send(buf, buf.length);
        }
    };

    private ActionListener receiverActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {

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
    };

    private ActionListener notReceiverActionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {

            rtpHandler.getReceiver().receive(datagramPacket);

            //create an RTPpacket object from the DP
            RTPpacket rtpPacket = new RTPpacket(datagramPacket.getData(), datagramPacket.getLength());
            int seqNumber = rtpPacket.getsequencenumber();
            //this is the highest seq num received

            //print just sequence number
            System.out.println("Got RTP packet with SeqNum # " + seqNumber);
        }
    };

    private ActionListener senderListener = isSender ? senderActionListener : notSenderActionListener;
    private ActionListener receiverListener = isReceiver ? receiverActionListener : notReceiverActionListener;

    //end a call
    public void sendBye() {
        if (!byeTaskRunning) {
            byeTaskRunning = true;
            rtpReceiverTimer.stop();
            rtpHandler.getReceiver().close();
            if (isReceiver) {
                speaker.drain();
                speaker.close();
            }
            Request byeRequest = null;
            try {
                byeRequest = dialog.createRequest(Request.BYE);
                Utils.addContent(headerFactory, byeRequest, rtpPort);
            } catch (SipException e) {
                e.printStackTrace();
            }
            byeTimer = new Timer();
            byeTimer.schedule(new Client.ByeTask(byeRequest), 4000);
        }
    }

    //start a call
    public void invite(String callee) {
        Request request = createRequset(Request.INVITE, "user", callee, "domain", "127.0.0.1:5080");
        createClientTransaction(request);
    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        System.out.println("Transaction Time out");
    }

    //connects the client class to the GUI and creates the sip tools
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
        //ha the listening point
        try {
            udpListeningPoint = sipStack.createListeningPoint(myIP, mySipPort, transport);
            System.out.println("listeningPoint = " + udpListeningPoint);
            sipProvider = sipStack.createSipProvider(udpListeningPoint);
            System.out.println("SipProvider = " + sipProvider);

            //implementing sip methods like processRequest
            sipProvider.addSipListener(this);
        } catch (Exception e) {
            printException(e);
        }
    }

    //the sip stack has the listening points and providers that are used in all the dialogs
    private void initStack(String serverAddress) {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.OUTBOUND_PROXY", serverAddress + "/"
                + transport);
        // If you want to use UDP then uncomment this.
        properties.setProperty("javax.sip.STACK_NAME", "client");

        // Drop the client connection after we are done with the transaction.
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS",
                "false");

        try {
            sipStack = sipFactory.createSipStack(properties);
            System.out.println("createSipStack " + sipStack);
        } catch (PeerUnavailableException e) {
            printException(e);
        }
    }

    //in order to create sip objects
    private void initFactories() {
        //used to create new stack objects and other factories
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        try {
            //used to create new headers objects
            headerFactory = sipFactory.createHeaderFactory();

            //used to create new URI's
            addressFactory = sipFactory.createAddressFactory();

            //used to create new requests and responses
            messageFactory = sipFactory.createMessageFactory();
        } catch (Exception e) {
            printException(e);
        }
    }

    public void register(String fromUsername, String fromDisplay, String fromDomain,
                         String myIP, int myPort, String serverAddress, IConnectSipToGUI iConnectSipToGUI) {
        init(myIP, myPort, serverAddress, iConnectSipToGUI);
        //can't call and receive calls before registering
        clientDetails = new ClientDetails(fromUsername, fromDisplay,
                fromDomain, myIP, myPort, serverAddress);
        Request request = createRequset(Request.REGISTER, "Server", null, "port80.com", serverAddress);
        createClientTransaction(request);
    }

    //the client sends requests in a clientTransaction and the server in a serverTransaction
    private void createClientTransaction(Request request) {
        try {
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);

            // send the request out
            if (dialog != null)
                dialog.sendRequest(clientTransaction);
            else clientTransaction.sendRequest();
            System.out.println();
            System.out.println("Sending Request:" + request.getMethod());
            System.out.println(request.getHeader(FromHeader.NAME) + "" + request.getHeader(ToHeader.NAME));

            dialog = clientTransaction.getDialog();
        } catch (Exception e) {
            printException(e);
        }
    }

    //creates all the relevant headers
    private Request createRequset(String method, String toUsername, String toDisplay, String toDomain, String serverAddress) {

        Request request = null;

        try {
            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(clientDetails.getUsername(), clientDetails.getDomainName());
            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(clientDetails.getDisplayName());
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
            SipURI contactURI = addressFactory.createSipURI(clientDetails.getUsername(), udpListeningPoint.getIPAddress());
            contactURI.setPort(udpListeningPoint.getPort());

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(clientDetails.getDisplayName());

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            Utils.addContent(headerFactory, request, rtpPort);
        } catch (Exception e) {
            printException(e);
        }

        return request;
    }

    private void printException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getMessage());
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
        dialog = null;
        isInConversation = false;
        iConnectSipToGUI.handleBye();
    }
}
