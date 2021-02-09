package rtp;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class RTPReciever {

    private DatagramSocket socket;

    public RTPReciever(int port){

        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(500);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void receive(DatagramPacket datagramPacket){
        try {
            socket.receive(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        socket.close();
    }
}
