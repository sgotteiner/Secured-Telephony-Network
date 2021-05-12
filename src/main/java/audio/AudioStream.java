package audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.applet.AudioClip;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AudioStream {

    AudioFormat format;
    TargetDataLine microphone;

    //-----------------------------------
    //constructor
    //-----------------------------------
    public AudioStream() throws Exception {

        // the bigger the sampleSizeInBits the more accurate the recording is and the more memory it captures
        format = new AudioFormat(8000, 16, 1, true, true);

        microphone = AudioSystem.getTargetDataLine(format);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();
    }

    //-----------------------------------
    // getnextframe
    //returns the next frame as an array of byte and the size of the frame
    //-----------------------------------
    public int getNextFrame(byte[] frame) throws Exception {
        return microphone.read(frame, 0, frame.length);
    }

    public void closeMic(){
        microphone.close();
    }
}
