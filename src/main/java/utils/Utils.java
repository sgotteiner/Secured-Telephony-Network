package utils;

import javax.sip.header.ContactHeader;
import javax.sip.message.Message;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class Utils {

    public static int getRandomPort(){
        Random r = new Random();
        while(true){
            int port = r.nextInt(20);
            try {
                new ServerSocket(port + 6000).close();
                return port + 6000;
            } catch (IOException e) {

            }
        }
    }

    public static int extractPortFromSdp(Object sdp) {
        return Integer.parseInt(new String((byte[]) sdp, StandardCharsets.UTF_8).split("m=audio ")[1].split(" ")[0]);
    }

    public static String getSenderIPfromMessage(Message message) {
        return message.getHeader(ContactHeader.NAME).toString().split("@")[1].split(":")[0];
    }
}
