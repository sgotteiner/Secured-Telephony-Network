package rtp;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class RTPHandler {

    private RTPSender sender;
    private RTPReciever receiver;
    private Timer timer;
    private boolean isServer;

    public RTPHandler(String ip, int sendPort, DatagramSocket receiveSocket, boolean isServer) {

        System.out.println("handler created: receives at " + receiveSocket.getLocalPort() + ", sends to " + sendPort);
        this.sender = new RTPSender(ip, sendPort);
        this.receiver = new RTPReciever(receiveSocket);
        this.isServer = isServer;

        if (isServer) {
            byte[] buf = new byte[1024];
            DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
            timer = new Timer(100, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    receiver.receive(datagramPacket);
                    sender.send(buf, buf.length);
                }
            });
            timer.start();
        }
    }

    public RTPSender getSender() {
        if (isServer)
            timer.stop();
        return sender;
    }

    public RTPReciever getReceiver() {
        return receiver;
    }

    public void closeAll() {
        if (isServer)
            timer.stop();
        sender.close();
        receiver.close();

    }
}
