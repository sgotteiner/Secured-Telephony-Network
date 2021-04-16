package audio;

public class AudioCalculator {

    private byte[] bytes;
    private int audioLength;
    private double decibel;

    public AudioCalculator() {
    }

    public void setBytes(byte[] bytes, int audioLength) {
        this.bytes = bytes;
        this.audioLength = audioLength;
    }

    public double getDecibel() {
        float sum = 0;
        for (int i = 0; i < audioLength; i++) {
            float sample = bytes[i];
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / audioLength);
        decibel = 20 * Math.log10(rms);
        return decibel;
    }

    public double getPrinstonFrequency() {
        Complex[] afterFFT = fft(generateComplex(bytes, audioLength));
        double sum = 0;
        for (int i = 0; i < audioLength / 4; i++) {
            //amplitude = height of wave = size of vector = magnitude
            sum += afterFFT[i].magnitude();
        }

        // average frequency = average amplitude * sample rate / (size of fft = number of samples)
        return (sum / (float) (audioLength / 2)) * (double) (8000 / audioLength);
    }

    private Complex[] generateComplex(byte[] bytes, int length) {

        Complex[] complexArr = new Complex[length / 2];
        for (int i = 0; i < length / 2; i++) {
            complexArr[i] = new Complex((short) bytes[2 * i], 0);
        }
        return complexArr;
    }

    //https://introcs.cs.princeton.edu/java/97data/FFT.java
    //prinston's fft function on complex array
    public static Complex[] fft(Complex[] x) {
        int n = x.length;

        // base case
        if (n == 1) return new Complex[]{x[0]};

        // radix 2 Cooley-Tukey FFT
        if (n % 2 != 0) {
            throw new IllegalArgumentException("n is not a power of 2");
        }

        // compute FFT of even terms
        Complex[] even = new Complex[n / 2];
        for (int k = 0; k < n / 2; k++) {
            even[k] = x[2 * k];
        }
        Complex[] evenFFT = fft(even);

        // compute FFT of odd terms
        Complex[] odd = even;  // reuse the array (to avoid n log n space)
        for (int k = 0; k < n / 2; k++) {
            odd[k] = x[2 * k + 1];
        }
        Complex[] oddFFT = fft(odd);

        // combine
        Complex[] y = new Complex[n];
        for (int k = 0; k < n / 2; k++) {
            double kth = -2 * k * Math.PI / n;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k] = evenFFT[k].plus(wk.times(oddFFT[k]));
            y[k + n / 2] = evenFFT[k].minus(wk.times(oddFFT[k]));
        }
        return y;
    }
}