package rtp;

import utils.Utils;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class RTPReciever {

    private DatagramSocket socket;
    private int port;

    public RTPReciever(DatagramSocket datagramSocket) {

        this.port = datagramSocket.getLocalPort();

        socket = datagramSocket;
        try {
            socket.setSoTimeout(200);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void receive(DatagramPacket datagramPacket) {
        try {
            if (!socket.isClosed())
                socket.receive(datagramPacket);
        } catch (IOException e) {
            System.out.println(e.getMessage() + " on receiver port: " + port);
            e.printStackTrace();
        }
    }

    public void close() {
        socket.close();
        System.out.println("Receiver port: " + port);
        Utils.printCurrentTime(port, "close", "receiver");
    }

    public boolean isClosed() {
        return socket.isClosed();
    }
}
