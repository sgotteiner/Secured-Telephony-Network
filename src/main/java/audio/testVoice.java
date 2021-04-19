package audio;

import audio.AudioCalculator;
import audio.AudioStream;
import rtp.RTPReciever;
import rtp.RTPSender;

import javax.sound.sampled.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Handler;

public class testVoice {

    private static AudioStream audio = null;
    private static byte[] buf;
    private static DatagramPacket datagramPacket;
    private static SourceDataLine speaker;
    private static boolean flag = true;

    public static void main(String[] args) {

        RTPSender rtpSender = new RTPSender("localhost", 10000);
        DatagramSocket datagramSocket = null;
        try {
            datagramSocket = new DatagramSocket(10000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        RTPReciever rtpReciever = new RTPReciever(datagramSocket);

        buf = new byte[1024];
        datagramPacket = new DatagramPacket(buf, buf.length);

        try {
            audio = new AudioStream();
        } catch (Exception e) {
            e.printStackTrace();
        }

        AudioFormat format = new AudioFormat(8000, 16, 1, true, true);
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
        try {
            speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            speaker.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        speaker.start();

        AudioCalculator audioCalculator = new AudioCalculator();


        Timer sendTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //get next frame to send from the video, as well as its size
                int audioLength = 0;
                try {
                    audioLength = audio.getnextframe(buf);
                    audioCalculator.setBytes(buf, audioLength);
                    double decibel = audioCalculator.getDecibel();
                    double frequency = audioCalculator.getPrinstonFrequency();
                    System.out.println("Frequency: " + frequency + ", Decibel: " + decibel);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                rtpSender.send(buf, audioLength, true);
            }
        });

        Timer receiveTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rtpReciever.receive(datagramPacket);

                //create an rtp.RTPpacket object from the DP
                rtp.RTPpacket rtpPacket = new rtp.RTPpacket(datagramPacket.getData(), datagramPacket.getLength());
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

                if (seqNumber == 50)
                    flag = false;
            }
        });

        while (flag) {
            sendTimer.start();

            receiveTimer.start();
        }

        receiveTimer.stop();
        rtpReciever.close();
        sendTimer.stop();
        rtpSender.close();
        audio.closeMic();
//        System.exit(0);
    }
}
