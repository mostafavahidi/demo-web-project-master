package edu.csupomona.cs480.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.DoubleStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class AudioAPI {

	public double[] rawFreqCreator(String fileName) {
		// Create a global buffer size
		final int EXTERNAL_BUFFER_SIZE = 20000;
		// 128000

		// Get the location of the sound file
		File soundFile = new File(fileName);

		// Load the Audio Input Stream from the file
		AudioInputStream audioInputStream = null;
		try {
			audioInputStream = AudioSystem.getAudioInputStream(soundFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Get Audio Format information
		AudioFormat audioFormat = audioInputStream.getFormat();

		// Handle opening the line
		SourceDataLine line = null;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		try {
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(audioFormat);
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Start playing the sound
		// line.start();

		// Write the sound to an array of bytes
		int nBytesRead = 0;
		byte[] abData = new byte[EXTERNAL_BUFFER_SIZE];
		while (nBytesRead != -1) {
			try {
				nBytesRead = audioInputStream.read(abData, 0, abData.length);

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (nBytesRead >= 0) {
				int nBytesWritten = line.write(abData, 0, nBytesRead);
			}

		}

		// close the line
		line.drain();
		line.close();

		// Calculate the sample rate
		float sample_rate = audioFormat.getSampleRate();
		System.out.println("sample rate = " + sample_rate);

		// Calculate the length in seconds of the sample
		float T = audioInputStream.getFrameLength() / audioFormat.getFrameRate();
		System.out.println("T = " + T + " (length of sampled sound in seconds)");

		// Calculate the number of equidistant points in time
		int n = (int) (T * sample_rate) / 2;
		System.out.println("n = " + n + " (number of equidistant points)");

		// Calculate the time interval at each equidistant point
		float h = (T / n);
		System.out.println("h = " + h + " (length of each time interval in second)");

		final int mNumberOfFFTPoints = 8192;
		// 1024
		double mMaxFFTSample;

		double temp;
		Complex[] y;
		Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
		double[] absSignal = new double[mNumberOfFFTPoints / 2];

		for (int i = 0; i < mNumberOfFFTPoints; i++) {
			temp = (double) ((abData[2 * i] & 0xFF) | (abData[2 * i + 1] << 8)) / 32768.0F;
			complexSignal[i] = new Complex(temp, 0.0);
		}

		y = FFT.fft(complexSignal); // Where FFT Class is used.

		mMaxFFTSample = 0.0;
		int mPeakPos = 0;
		for (int i = 0; i < (mNumberOfFFTPoints / 2); i++) {
			absSignal[i] = Math.sqrt(Math.pow(y[i].re(), 2) + Math.pow(y[i].im(), 2));
			if (absSignal[i] > mMaxFFTSample) {
				mMaxFFTSample = absSignal[i];
				mPeakPos = i;
			}
			double amplitude = 2 * absSignal[i] / n;
			double frequency = i * h / T * sample_rate;
			System.out.println("frequency = " + frequency + ", amp = " + amplitude);
		}
		System.out.println("DONE");
		return absSignal;
	}

	public double[] compressBands(double[] rawFreqs, double[] compressedBand, int numRaw, int numCompressed) {
		if (rawFreqs == null || compressedBand == null || numRaw < 1 || numCompressed < 1) {
			System.out.println("The input array is not important/large enough.");
		}
		// Start with 1Hz frequency
		double centerFreq = 1.0;
		double lowerFreqBound, upperFreqBound;
		int lowerIndex, upperIndex;
		// Work out N, which is the number of steps per
		// octave in the compressed band
		// If numCompressed is 202 this works out to exactly 24
		// steps per octave i.e. each step is a quarter note.
		double N = (numCompressed - 2) / (8.0 + 1.0 / 3.0);
		// Note that we skip the first few octaves as these are
		// in a frequency range (0-20Hz) that does not interest us
		int freqIter = (int) N * 6;
		double ten_N_log = 10.0 * N * Math.log10(2.0);
		for (int i = 0; i < numCompressed; i++) {
			compressedBand[i] = 0;
			// First calculate the frequencies;
			// We start with a frequency around 20Hz
			centerFreq = Math.pow(2.0, (3.0 * (freqIter)) / ten_N_log);
			freqIter++;
			lowerFreqBound = centerFreq / Math.pow(2.0, 1.0 / (2.0 * N));
			upperFreqBound = centerFreq * Math.pow(2.0, 1.0 / (2.0 * N));
			// Then convert them to array indices using the formula
			// index = Frequency*arraySize/(maxFreq)*2
			// NOTE max Freq = 22050 as we are sampling at 44100Hz
			lowerIndex = (int) (lowerFreqBound * ((double) numRaw) / 44100.0);
			upperIndex = (int) (upperFreqBound * ((double) numRaw) / 44100.0);
			if (upperIndex > numRaw)
				upperIndex = numRaw;
			if (upperIndex == lowerIndex)
				lowerIndex--;
			if (lowerIndex < 0)
				lowerIndex = 0;
			// Finally make the compressed band index be the mean of values
			// in the uncompressed band
			for (int j = lowerIndex; j < upperIndex; j++) {
				compressedBand[i] += rawFreqs[j];
			}
			if (upperIndex != lowerIndex)
				compressedBand[i] /= upperIndex - lowerIndex;
		}
		System.out.println("COMPRESSED BAND CREATED.");

		return compressedBand;
	}

	public double[][] linearFreqMatrixCreator() {

		double[][] linearFreqMatrix = new double[5][];

		File folder = new File("/Users/Mostafa/workspace/AudioClassifier/");
		File[] listOfFiles = folder.listFiles(new FilenameFilter() {
			public boolean accept(File folder, String name) {
				return name.toLowerCase().endsWith(".wav");
			}
		});

		int rowCounter = 0;

		for (File file : listOfFiles) {
			double[] rawFreqBand = rawFreqCreator(file.getName());
			double[] compressedBand = new double[rawFreqBand.length];
			linearFreqMatrix[rowCounter] = compressBands(rawFreqBand, compressedBand, rawFreqBand.length,
					compressedBand.length);
			rowCounter++;
		}

		return linearFreqMatrix;

	}

	public void attributeExtractor(double[][] linearArrayMatrix) throws Exception {
		PrintWriter writer = new PrintWriter("TestAudio.arff", "UTF-8");
		writer.println("@relation Audio-weka.filters.unsupervised.instance.NonSparseToSparse");
		writer.println("@attribute 'Volume' real");
		writer.println("@attribute 'Number of Extreme Frequencies' real");
		writer.println("@attribute 'Spectrul Flux' real");
		writer.println("@attribute 'Type' {commercial, noncommercial}");
		writer.println("");
		writer.println("@data");

		for (int row = 0; row < linearArrayMatrix.length; row++) {
			// Creating Volume Attribute value.
			double sumFreq = 0;
			for (int a = 0; a < linearArrayMatrix[row].length; a++) {
				sumFreq += linearArrayMatrix[row][a];
			}
			double avgFreqValue = sumFreq / linearArrayMatrix[row].length;

			// Getting data for Volume attribute.
			double[] squaredLinearArray = new double[linearArrayMatrix[row].length];
			for (int j = 0; j < squaredLinearArray.length; j++) {
				squaredLinearArray[j] = Math.pow(linearArrayMatrix[row][j], 2.0);
				System.out.println("Creating volume attribute.");
			}
			double volume = DoubleStream.of(squaredLinearArray).sum();

			// Creating Silent Frequency attribute value.
			// double[] silentFreqArray = new double[32];
			// int numSilentFreq = 0;
			// for (int l = 0; l < 32; l++) {
			// silentFreqArray[l] = Math.pow(2.0, (l + 1) / 2.89) + 300;
			// System.out.println("Creating Silent Frequency attribute.");
			// }
			// for (int s = 0; s < silentFreqArray.length; s++) {
			// if (silentFreqArray[s] < 0.15 * avgFreqValue) {
			// numSilentFreq++;
			// }
			// System.out.println("Creating Silent Frequency 2 attribute.");
			// }
			// double silentFreq = numSilentFreq;

			// Creating Extreme Frequency attribute value.
			int numExtremeFreq = 0;
			for (int d = 0; d < linearArrayMatrix[row].length; d++) {
				if (linearArrayMatrix[row][d] < 0.2 * avgFreqValue || linearArrayMatrix[row][d] > 3 * avgFreqValue) {
					numExtremeFreq++;
				}
				System.out.println("Creating Extreme Frequency attribute.");
			}
			double extremeFreq = numExtremeFreq;

			// Creating Spectrul Flux attribute value.
			double specFlux = 0;
			double[] specFluxData = new double[(linearArrayMatrix[row].length) - (linearArrayMatrix[row].length / 4)];
			int indexCounter = 0;
			for (int j = 0; j < specFluxData.length; j++) {
				specFluxData[j] = Math.abs(linearArrayMatrix[row][indexCounter]
						- (linearArrayMatrix[row][indexCounter + (linearArrayMatrix[row].length / 4)]));
				indexCounter++;
			}
			double spectrulFlux = DoubleStream.of(specFluxData).sum();

			// Creating type attribute value *unknown*
			String type = "?";

			writer.print(volume);
			writer.print(",");
			// writer.print(silentFreq);
			// writer.print(",");
			writer.print(extremeFreq);
			writer.print(",");
			writer.print(spectrulFlux);
			writer.print(",");
			writer.print(type);
			writer.println();
		}

		writer.close();
		System.out.println("ARFF FILE CREATED!");
	}

	public void waveSplitter(File audioInput) {
		final int SPLIT_FILE_LENGTH_MS = 1000;
		try {
			// Get the wave file from the embedded resources
			File soundFile = audioInput;
			WavFile inputWavFile = WavFile.openWavFile(soundFile);
			// Get the number of audio channels in the wav file
			int numChannels = inputWavFile.getNumChannels();
			// set the maximum number of frames for a target file,
			// based on the number of milliseconds assigned for each file
			int maxFramesPerFile = (int) inputWavFile.getSampleRate() * SPLIT_FILE_LENGTH_MS / 1000;

			// Create a buffer of maxFramesPerFile frames
			double[] buffer = new double[maxFramesPerFile * numChannels];

			int framesRead;
			int fileCount = 0;
			do {
				// Read frames into buffer
				framesRead = inputWavFile.readFrames(buffer, maxFramesPerFile);
				WavFile outputWavFile = WavFile.newWavFile(new File("out" + (fileCount + 1) + ".wav"),
						inputWavFile.getNumChannels(), framesRead, inputWavFile.getValidBits(),
						inputWavFile.getSampleRate());

				// Write the buffer
				outputWavFile.writeFrames(buffer, framesRead);
				outputWavFile.close();
				fileCount++;
			} while (framesRead != 0);

			// Close the input file
			inputWavFile.close();

		} catch (Exception e) {
			System.err.println(e);
		}
	}

	public boolean predictor(File audioInput) throws Exception {
//		// Split the 5 second audio file into five, 1 second long audio files.
//		waveSplitter(audioInput);

		// Extract all the attributes from these files and make ARFF file
		// ("TestAudio.Arff")
		attributeExtractor(linearFreqMatrixCreator());

		// Load training dataset
		DataSource source = new DataSource("/users/Mostafa/Workspace/AudioClassifier/TrainingAudio.arff");
		Instances trainDataSet = source.getDataSet();

		// Set Class index to the last attribute
		trainDataSet.setClassIndex(trainDataSet.numAttributes() - 1);

		// Creating bayes classifier!
		NaiveBayesMultinomial bayes = new NaiveBayesMultinomial();
		bayes.buildClassifier(trainDataSet);

		// Load Test dataset
		DataSource source1 = new DataSource("/users/Mostafa/Workspace/AudioClassifier/TestAudio.arff");
		Instances testDataSet = source1.getDataSet();

		// Set class index to the last attribute
		testDataSet.setClassIndex(testDataSet.numAttributes() - 1);

		int commerciallyClassified = 0;
		int noncommerciallyClassified = 0;
		
		for (int i = 0; i < testDataSet.numInstances(); i++) {
			// Get instance object of current instance.
			Instance newInst = testDataSet.instance(i);
			// Call classifyInstance, which returns a double value for the
			// class.
			double predBayes = bayes.classifyInstance(newInst);
			// Use this value to get string value of the predicted class.
			String predString = testDataSet.classAttribute().value((int) predBayes);
			System.out.println("Predicted: " + predString);
			if (predString.equals("commercial")){
				commerciallyClassified++;
			} else {
				noncommerciallyClassified++;
			}
		}
		
		System.out.println(commerciallyClassified);
		System.out.println(noncommerciallyClassified);
		
		if (commerciallyClassified > noncommerciallyClassified){
			return true;
		} else {
			return false;
		}
	}

}
