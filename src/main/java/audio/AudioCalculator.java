package audio;

public class AudioCalculator {

    private byte[] bytes;
    private int audioLength;
    private double frequency;
    private double decibel;

    public AudioCalculator() {
    }

    public void setBytes(byte[] bytes, int audioLength) {
        this.bytes = bytes;
        this.audioLength = audioLength;
        frequency = 0.0D;
        decibel = 0.0D;
    }

    public double getFrequency() {
        if (frequency == 0.0D) frequency = retrieveFrequency();
        return frequency;
    }

    private double retrieveFrequency() {
        int length = audioLength / 2;
        int sampleSize = 8192;
        while (sampleSize > length) sampleSize = sampleSize >> 1;

        FrequencyCalculator frequencyCalculator = new FrequencyCalculator(sampleSize);
        frequencyCalculator.feedData(bytes, length);

        return resizeNumber(frequencyCalculator.getFreq());
    }

    public double resizeNumber(double value) {
        int temp = (int) (value * 10.0d);
        return temp / 10.0d;
    }

    public double getDecibel(){
        float sum = 0;
        for (int i = 0; i < audioLength; i++) {
            sum += bytes[i] * bytes[i];
        }
        double rms = Math.sqrt(sum / audioLength);
        decibel = 20 * Math.log10(rms);
        return decibel;
    }
}