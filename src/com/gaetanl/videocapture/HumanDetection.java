package com.gaetanl.videocapture;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.InputMismatchException;
import java.util.Scanner;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public class HumanDetection {
	private final static String RESOURCES_PATH = "resources/";
	private final static String DET_FRAME_PATH = "frame/";
	private final static String HAAR_FR_FACE_1 = "haarcascade_frontalface_default.xml";
	private final static String HAAR_FULL_BODY = "haarcascade_fullbody.xml";
	private final static String HAAR_UPPR_BODY = "haarcascade_upperbody.xml";
	private final static String CAPTURE_WINDOW = "Human detection";
	private final static int FRAMERATE = 200;
	private final static DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private final static DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_hhmmssSSS");

	/*
	 * Maximum amount of time between two detections for them to be considered
	 * continuous.
	 */
	private final static int MAX_TIME_FOR_CONTINUOUS = FRAMERATE * 2;

	/*
	 * Number of continuous detections.
	 */
	private static int continuousDetections;

	/*
	 * Beginning of the current continuous detection.
	 */
	private static LocalDateTime continuousDetectionStart;

	/*
	 * Time of the last detection.
	 */
	private static LocalDateTime lastDetectionTime;

	/*
	 * Minimum continuous detection time before signaling a person is really
	 * present.
	 */
	private final static int MIN_CONTINUOUS_TIME_BEFORE_SIGNALING = 2000 / MAX_TIME_FOR_CONTINUOUS;

	/*
	 * Minimum time between signaling calls.
	 */
	private final static int MIN_TIME_BETWEEN_SIGNALING = 1000 / MAX_TIME_FOR_CONTINUOUS;

	/*
	 * Time of the last signaling.
	 */
	private static LocalDateTime lastSignalingTime;
	

	// Has to be loaded before instantiating frames
	static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	private static final Scanner SCANNER = new Scanner(System.in);
	private static final VideoCapture CAPTURE = new VideoCapture(Videoio.CAP_DSHOW);;
	private static final Mat FRAME = new Mat();
	private static final Mat GRAY_FRAME = new Mat();
	private static final MatOfRect DETECTION_FRAME = new MatOfRect();

    public static void main(String[] args) throws InterruptedException {
        boolean fileExists = false;
        String filePath;
        CascadeClassifier humanDetector;

        // Selecting type of detection
        while (!fileExists) {
            System.out.println("Choose detection type");
            System.out.println("  1. Frontal face");
            System.out.println("  2. Full body");
            System.out.println("  3. Upper body");
            System.out.println("  0. Exit program");
            try {
            	final int choice = Integer.parseInt(SCANNER.nextLine());
            	filePath = RESOURCES_PATH;
            	humanDetector = new CascadeClassifier();

	            switch (choice) {
	            	case 1:
	            	case 2:
	            	case 3:
	            		filePath += (new String[] {HAAR_FR_FACE_1, HAAR_FULL_BODY, HAAR_UPPR_BODY})[choice - 1];
	            		break;
	            	case 0:
	            		exit(0);
	        		default:
	        			throw new InputMismatchException();
	            }

	            loasClassifier(filePath, humanDetector);
            }
            catch (InputMismatchException | FileNotFoundException | IllegalArgumentException e) {
                System.out.println(e.getMessage() == null ? "Invalid choice" : String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()));
        		continue;
            }

            captureLoop(humanDetector);
        }
    }

    private static void loasClassifier(final String path, final CascadeClassifier humanDetector) throws FileNotFoundException, IllegalArgumentException {
        if (!(new File(path)).exists()) throw new FileNotFoundException(String.format("File %s not found", path));
        if (!humanDetector.load(path))  throw new IllegalArgumentException(String.format("Couldn't load CascadeClassifier with %s", path));
        System.out.println(String.format("Loaded CascadeClassifier with %s", path));
    }

    private static void captureLoop(final CascadeClassifier humanDetector) {
        // Displaying monitor
        HighGui.namedWindow(CAPTURE_WINDOW, HighGui.WINDOW_AUTOSIZE);

        // Initialize video capture from camera
        if (!CAPTURE.isOpened()) {
            System.err.println("Video capture is not open");
            System.err.println("Check that camera is not in use in another program or running instance of Java");

            return;
        }

        // Detection loop
        while (CAPTURE.read(FRAME)) {
            // Conversion to greyscale
            Imgproc.cvtColor(FRAME, GRAY_FRAME, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(GRAY_FRAME, GRAY_FRAME);

            // Human detection
            humanDetector.detectMultiScale(GRAY_FRAME, DETECTION_FRAME, 1.1, 2, 0, new Size(30, 30), new Size());

            // Highlighting detected objects
            for (Rect rect: DETECTION_FRAME.toArray()) {
                Imgproc.rectangle(FRAME, rect.tl(), rect.br(), new Scalar(0, 255, 0), 2);

                long timeDifference = 0;
                LocalDateTime time = LocalDateTime.now();

                if (lastDetectionTime != null) {
	                timeDifference = lastDetectionTime.until(time, ChronoUnit.MILLIS);
                }

            	lastDetectionTime = time;
            	boolean resetContinuous = false;
            	boolean signaling = false;
            	boolean minContinuous = false;
            	boolean minBeforeSignaling = false;
            	boolean savedImage = false;

            	resetContinuous = (continuousDetections > 1) && (timeDifference > MAX_TIME_FOR_CONTINUOUS);
                if (resetContinuous) {
                	// Too long since last detection, reseting lastDetectionTime
                	continuousDetections = 1;
                	continuousDetectionStart = time;
                }
                else {
                	continuousDetections++;
                	if (continuousDetectionStart == null) continuousDetectionStart = time;

                	minContinuous = continuousDetections > MIN_CONTINUOUS_TIME_BEFORE_SIGNALING;
                	minBeforeSignaling = (lastSignalingTime == null) || lastSignalingTime.until(time, ChronoUnit.SECONDS) > MIN_TIME_BETWEEN_SIGNALING;
                	signaling = minContinuous && minBeforeSignaling;
                    if (signaling) {
                    	lastSignalingTime = time;

                    	// Saving frame to image
                    	if (DET_FRAME_PATH != null) {
	                    	String imagePath = String.format("%sframe_%s.jpg", DET_FRAME_PATH, FILE_TIME_FORMATTER.format(time));
	                    	savedImage = Imgcodecs.imwrite(imagePath, FRAME);
                    	}
                    }
                }

                StringBuilder messageSb = new StringBuilder();
                String level = "[INFO]";
                if (signaling) {
                	if (savedImage) {
                		level = "[OK]";
                		messageSb.append("Saved image to project_path/frame/");
                	}
                	else {
                		level = "[NOK]";
                		messageSb.append("Failed to save image to project_path/frame/\"");
                	}
                }
                else {
                	if (resetContinuous) {
                		messageSb.append("Continuous detection broken");
                	}
                	else if (!minContinuous) messageSb.append("Insufficient continuous detection");
                	else if (!minBeforeSignaling) messageSb.append("Insufficient delay since last signaling");
                }

                System.out.println(String.format(" %-7s  TIME: %s  |  EVNT: %9s  |  STRT: %s  |  CONT: %6dms  |  ITER: %6d  |  DIFF: %6dms  |  SGNL: %9s  |  MESG: %s",
                		level,
                		TIME_FORMATTER.format(time),
                		"Detection",
                		(continuousDetectionStart == null ? "            " : TIME_FORMATTER.format(continuousDetectionStart)),
                		(continuousDetectionStart == null ? "            " : continuousDetectionStart.until(time, ChronoUnit.MILLIS)),
                		continuousDetections,
                		timeDifference,
                		signaling ? "Signaling" : "Inhibited",
                		messageSb.toString()));
            }

            // Displaying image with highlighted objects
            HighGui.imshow("Human detection", FRAME);

            // Exit loop on 'q' key press
            if (KeyEvent.VK_Q == HighGui.waitKey(FRAMERATE)) {
                return;
            }
        }
    }

    private static void exit(final int exitCode) throws InterruptedException {
    	// Releasing resources
    	if (SCANNER != null) SCANNER.close();
        System.out.println("Scanner closed");

    	if (CAPTURE != null) CAPTURE.release();
        System.out.println("Video capture stopped");

        if (FRAME != null) FRAME.release();
        System.out.println("Frame released");

        if (GRAY_FRAME != null) GRAY_FRAME.release();
        System.out.println("Gray frame released");

        if (DETECTION_FRAME != null) DETECTION_FRAME.release();
        System.out.println("Detection frame released");

        HighGui.destroyWindow(CAPTURE_WINDOW);
        System.out.println("Window destroyed");

    	System.out.println("Exiting program");
    	System.exit(exitCode);
    }
}