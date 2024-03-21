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

/**
 * Detection of human face/body in video feed.
 *
 * @author Gaetan L.
 */
public class HumanDetection {
	/**
	 * Path to folder containing Haar Cascade Classifier files, relative to
	 * project root folder.
	 */
	private final static String RESOURCES_PATH = "resources/";

	/**
	 * Path to folder used to save signaling frames, relative to project root
	 * folder.
	 */
	private final static String DET_FRAME_PATH = "frame/";



	/**
	 * Name of the Haar Cascade Classifier file detecting face. 
	 */
	private final static String HAAR_FR_FACE_1 = "haarcascade_frontalface_default.xml";

	/**
	 * Name of the Haar Cascade Classifier file detecting full body. 
	 */
	private final static String HAAR_FULL_BODY = "haarcascade_fullbody.xml";

	/**
	 * Names of the Haar Cascade Classifier file detecting upper body. 
	 */
	private final static String HAAR_UPPR_BODY = "haarcascade_upperbody.xml";

	/**
	 * Name of the widow displaying video stream.
	 */
	private final static String CAPTURE_WINDOW = "Human detection";



	/**
	 * Time between each capture in the capture loop, in milliseconds.
	 */
	private final static int FRAMERATE         =  100;

	/**
	 * Maximum time between two detections for them to be considered
	 * continuous.
	 */
	private final static int CTIME_BREAK       =  200;

	/**
	 * Minimum continuous detection time before signaling.
	 */
	private final static int MIN_CTIME_SIGNAL  = 1000;

	/**
	 * Minimum time between each signaling calls.
	 */
	private final static int MIN_TIME_RESIGNAL = 2000;



	/**
	 * Used for logging.
	 */
	private final static DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	/**
	 * Used for file naming.
	 */
	private final static DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_hhmmssSSS");



	/**
	 * Beginning of the current continuous detection.
	 */
	private static LocalDateTime continuousDetectionStart;

	/**
	 * Time of the last detection.
	 */
	private static LocalDateTime lastDetectionTime;

	/**
	 * Time of the last signaling.
	 */
	private static LocalDateTime lastSignalingTime;
	

	/*
	 * Has to be loaded before instantiating frames.
	 */
	static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	/**
	 * User input scanner.
	 */
	private static final Scanner SCANNER = new Scanner(System.in);

	/**
	 * OpenCV class in charge of video capture.
	 */
	private static final VideoCapture CAPTURE = new VideoCapture(Videoio.CAP_DSHOW);

	/**
	 * OpenCV class in charge of storing the values of a frame.
	 */
	private static final Mat FRAME = new Mat();

	/**
	 * OpenCV class in charge of transforming a frame to greyscale.
	 */
	private static final Mat GRAY_FRAME = new Mat();

	/**
	 * OpenCV class in charge of displaying detected objects.
	 */
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

	            loadClassifier(filePath, humanDetector);
            }
            catch (InputMismatchException | FileNotFoundException | IllegalArgumentException e) {
                System.out.println(e.getMessage() == null ? "Invalid choice" : String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()));
        		continue;
            }

            captureLoop(humanDetector);
        }
    }

    /**
     * Loads the Haar Cascade Classifier corresponding to the specified file
     * into the CascadeClassifier object.
     *
     * @param  path -------------------- path of the XML file containing the
     *                                   Haar Cascade Classifier
     * @param  humanDetector ----------- CascadeClassifier object into which
     *                                   the file is loaded
     *
     * @throws FileNotFoundException --- if the file is not found
     * @throws IllegalArgumentException  if the CascadeClassifier object
     *                                   couldn't load the file 
     */
    private static void loadClassifier(final String path, final CascadeClassifier humanDetector) throws FileNotFoundException, IllegalArgumentException {
        if (!(new File(path)).exists()) throw new FileNotFoundException(String.format("File %s not found", path));
        if (!humanDetector.load(path))  throw new IllegalArgumentException(String.format("Couldn't load CascadeClassifier with %s", path));
        System.out.println(String.format("Loaded CascadeClassifier with %s", path));
    }

    /**
     * Loop doing the video capture and detection. HighGui.waitKey(FRAMERATE)
     * insures a minimum duration (FRAMERATE value) between two iterations.
     *
     * @param  humanDetector  the CascadeClassifier used for detection
     */
    private static void captureLoop(final CascadeClassifier humanDetector) {
        // Window for displaying results
        HighGui.namedWindow(CAPTURE_WINDOW, HighGui.WINDOW_AUTOSIZE);



        // Initialize video capture from camera
        if (!CAPTURE.isOpened()) {
            System.err.println("Video capture is not open");
            System.err.println("Check that camera is not in use in another program or running instance of Java");

            // Back to the choice input loop
            return;
        }



        // Detection loop
        while (CAPTURE.read(FRAME)) {
            // Conversion to greyscale
            Imgproc.cvtColor(FRAME, GRAY_FRAME, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(GRAY_FRAME, GRAY_FRAME);

            // Detecting and putting every detection into DETECTION_FRAME
            humanDetector.detectMultiScale(GRAY_FRAME, DETECTION_FRAME, 1.1, 2, 0, new Size(30, 30), new Size());
            Rect[] detections = DETECTION_FRAME.toArray();

            // Highlighting each detected object in FRAME
            for (Rect rect: detections) {
                Imgproc.rectangle(FRAME, rect.tl(), rect.br(), new Scalar(0, 255, 0), 2);
            }



            // Actions in case of detection(s)
            if (detections.length > 0) {
            	// (Re)initializing loop variables
                LocalDateTime time = LocalDateTime.now();
                long timeDifference = 0;
            	long continuousDetectionTime = 0;
            	boolean resetContinuous = false;
            	boolean signaling = false;
            	boolean minContinuous = false;
            	boolean minBeforeSignaling = false;
            	boolean savedImage = false;

                if (lastDetectionTime != null) timeDifference = lastDetectionTime.until(time, ChronoUnit.MILLIS);
            	lastDetectionTime = time;

            	resetContinuous = timeDifference > CTIME_BREAK;
                if (resetContinuous) {
                	// Too long since last detection, reseting lastDetectionTime
                	continuousDetectionStart = time;
                }
                else {
                	if (continuousDetectionStart == null) continuousDetectionStart = time;

                	continuousDetectionTime = continuousDetectionStart.until(time, ChronoUnit.MILLIS);
                	minContinuous = continuousDetectionTime > MIN_CTIME_SIGNAL;
                	minBeforeSignaling = (lastSignalingTime == null) || lastSignalingTime.until(time, ChronoUnit.MILLIS) > MIN_TIME_RESIGNAL;
                	signaling = minContinuous && minBeforeSignaling;
                    if (signaling) {
                    	lastSignalingTime = time;

                    	// TODO: Put actual signaling code here

                    	// Saving detected frame to image
                    	if (DET_FRAME_PATH != null) {
	                    	String imagePath = String.format("%sframe_%s.jpg", DET_FRAME_PATH, FILE_TIME_FORMATTER.format(time));
	                    	savedImage = Imgcodecs.imwrite(imagePath, FRAME);
                    	}
                    }
                }



                // Logging information
                StringBuilder messageSb = new StringBuilder();
                String level = "[INFO]";
                if (signaling) {
                	if (savedImage) {
                		messageSb.append("[OK] Saved image to project_path/frame/");
                	}
                	else {
                		messageSb.append("[NOK] Failed to save image to project_path/frame/\"");
                	}
                }
                else {
                	if (resetContinuous) {
                		messageSb.append("Continuous detection broken");
                	}
                	else if (!minContinuous) messageSb.append("Insufficient continuous detection");
                	else if (!minBeforeSignaling) messageSb.append("Insufficient delay since last signaling");
                }

                System.out.println(String.format(" %-7s  TIME: %s  |  EVNT: %9s  |  STRT: %s  |  CONT: %6dms  |  DIFF: %6dms  |  SGNL: %9s  |  MESG: %s",
                		level,
                		TIME_FORMATTER.format(time),
                		"Detection",
                		(continuousDetectionStart == null ? "            " : TIME_FORMATTER.format(continuousDetectionStart)),
                		(continuousDetectionStart == null ? "            " : continuousDetectionStart.until(time, ChronoUnit.MILLIS)),
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

    /**
     * Exits the program after releasing the various OpenCV resources.
     *
     * @param  exitCode  Code returned to System.exit
     */
    private static void exit(final int exitCode) {
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