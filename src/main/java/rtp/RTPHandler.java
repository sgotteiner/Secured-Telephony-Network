package rtp;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;

public class RTPHandler {

    private RTPSender sender;
    private RTPReciever reciever;
    private Timer timer;
    private boolean isServer;

    public RTPHandler(String ip, int sendPort, int recievePort, boolean isServer) {
        this.sender = new RTPSender(ip, sendPort);
        this.reciever = new RTPReciever(recievePort);
        this.isServer = isServer;

        if (isServer) {
            timer = new Timer(100, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    byte[] buf = new byte[1024];
                    reciever.receive(new DatagramPacket(buf, buf.length));
                    sender.send(buf, buf.length);
                }
            });
            timer.start();
        }
    }

    public RTPSender getSender() {
        return sender;
    }

    public RTPReciever getReciever() {
        return reciever;
    }

    public void close() {
        if (isServer)
            timer.stop();
        sender.close();
        reciever.close();
    }
}
