package rtp;

import audio.AudioCalculator;

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
    private IProxyToRTPCallBack iProxyToRTPCallBack;

    public RTPHandler(String ip, int sendPort, DatagramSocket receiveSocket, boolean isServer, IProxyToRTPCallBack iProxyToRTPCallBack) {

        System.out.println("handler created: receives at " + receiveSocket.getLocalPort() + ", sends to " + sendPort);
        this.sender = new RTPSender(ip, sendPort);
        this.receiver = new RTPReciever(receiveSocket);
        this.isServer = isServer;

        if (isServer) {
            this.iProxyToRTPCallBack = iProxyToRTPCallBack;
            byte[] buf = new byte[1036], emptyArray = new byte[1];
            DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
            AudioCalculator audioCalculator = new AudioCalculator();
            final int[] badFrequencyCounter = { 0 }, badVolumeCounter = { 0 };
            final double[] frequency = new double[1], volume = new double[1];
            final boolean[] isSend = {true};
            timer = new Timer(100, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    receiver.receive(datagramPacket);
                    //check the frequency and volume
                    rtp.RTPpacket rtpPacket = new rtp.RTPpacket(datagramPacket.getData(), datagramPacket.getLength());
                    int payloadLength = rtpPacket.getpayload_length();
                    byte[] payload = new byte[payloadLength];
                    rtpPacket.getpayload(payload);
                    audioCalculator.setBytes(payload, payloadLength);
                    frequency[0] = audioCalculator.getPrinstonFrequency();
                    volume[0] = audioCalculator.getDecibel();
                    System.out.println("Frequency: " + frequency[0] + ", Decibel: " + volume[0]);
                    //check if the audio can be heard
                    if (frequency[0] < 100 || frequency[0] > 20000) {
                        badFrequencyCounter[0]++;
                        isSend[0] = false;
                    } else {
                        badFrequencyCounter[0] = 0;
                    }
                    if (volume[0] < 34) {
                        badVolumeCounter[0]++;
                        isSend[0] = false;
                    } else {
                        badVolumeCounter[0] = 0;
                    }
                    if(isSend[0])
                        sender.send(buf, buf.length, false);
                    else {
                        emptyArray[0] = (byte) rtpPacket.getsequencenumber();
                        sender.send(emptyArray, 1, false);
                    }
                    isSend[0] = true;
                    //stop the call if this is a silent call
                    if(badFrequencyCounter[0] == 51 || badVolumeCounter[0] == 51)
                        iProxyToRTPCallBack.stopCall(ip + ":" + sendPort);
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

    public interface IProxyToRTPCallBack {
        public void stopCall(String sendAddress);
    }
}
