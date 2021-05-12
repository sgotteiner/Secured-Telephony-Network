package rtp;

import utils.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class RTPSender {

    private DatagramSocket socket; //socket to be used to send and receive UDP packets

    private String serverIP;
    private int port; //the port on server that will receive the RTP data

    private int seqNumber = 0;

    public RTPSender(String serverIP, int port){
        try {
            Utils.printCurrentTime(port, "open", "sender");
            this.socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        this.serverIP = serverIP;
        this.port = port;
    }

    public void send(byte[] data, int audioLength, boolean isAudio){
        DatagramPacket datagramPacket;
        try {
            if(isAudio) {
            //Builds an rtp.RTPpacket object containing the frame
            RTPpacket rtpPacket = new RTPpacket(0, seqNumber, (int) System.currentTimeMillis(), data, audioLength);

            //get to total length of the full rtp packet to send
            int packetLength = rtpPacket.getlength();

            //retrieve the packet bitstream and store it in an array of bytes
            byte[] packetBits = new byte[packetLength];
            rtpPacket.getpacket(packetBits);

            datagramPacket = new DatagramPacket(packetBits, packetLength, InetAddress.getByName(serverIP), port);
        } else {
            datagramPacket = new DatagramPacket(data, audioLength, InetAddress.getByName(serverIP), port);
        }
        //send the packet as a DatagramPacket over the UDP socket
            socket.send(datagramPacket);
        } catch (IOException e) {
            System.out.println(e.getMessage() + " on sender port: " + port);
            e.printStackTrace();
        }

        System.out.println("Send frame #" + seqNumber + ", Frame size: " + audioLength);
//        //print the header bitstream
//        rtpPacket.printheader();

        seqNumber++;
    }

    public void close(){
        System.out.println("Sender port: " + port);
        Utils.printCurrentTime(port, "closed", "sender");
        socket.disconnect();
        socket.close();
    }
}
