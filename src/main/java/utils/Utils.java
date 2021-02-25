package utils;

import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.message.Message;
import javax.sip.message.Request;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Random;

public class Utils {

    //TODO better function because this doesn't always work for some reason
    public static int getRandomPort() {
        Random r = new Random();
        while (true) {
            int port = r.nextInt(50);
            try {
                new ServerSocket(port + 6000).close();
                return port + 6000;
            } catch (IOException e) {

            }
        }
    }

    //add rtp port to a request or response
    public static void addContent(HeaderFactory headerFactory, Message message, int port) {
        ContentTypeHeader contentTypeHeader;
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

    //important for rtp
    public static int extractPortFromSdp(Object sdp) {
        return Integer.parseInt(new String((byte[]) sdp, StandardCharsets.UTF_8).split("m=audio ")[1].split(" ")[0]);
    }

    //important for rtp
    public static String getSenderIPfromMessage(Message message) {
        return message.getHeader(ContactHeader.NAME).toString().split("@")[1].split(":")[0];
    }

    //sort of a debugging tool that helps me know when ports were opened or closed
    public static void printCurrentTime(int port, String operation, String where) {
        System.out.println(port + " " + operation + " in " + where + " at: " + System.currentTimeMillis() % 100000);
    }

    public static String getAddress(Request request) {
        return getSenderIPfromMessage(request) + ":" + extractPortFromSdp(request.getContent());
    }
}
