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
public class HumanDetection implements Runnable, HumanDetectionMBean {
	/**
	 * Path to folder containing Haar Cascade Classifier files, relative to
	 * project root folder.
	 */
	private final String RESOURCES_PATH = "resources/";

	/**
	 * Path to folder used to save signaling frames, relative to project root
	 * folder.
	 */
	private final String DET_FRAME_PATH = "frame/";

	/**
	 * Determines if detection frames should be saved or not.
	 */
	private boolean FRAME_SAVING = true;



	/**
	 * Name of the Haar Cascade Classifier file detecting face. 
	 */
	private final String HAAR_FR_FACE_1 = "haarcascade_frontalface_default.xml";

	/**
	 * Name of the Haar Cascade Classifier file detecting full body. 
	 */
	private final String HAAR_FULL_BODY = "haarcascade_fullbody.xml";

	/**
	 * Names of the Haar Cascade Classifier file detecting upper body. 
	 */
	private final String HAAR_UPPR_BODY = "haarcascade_upperbody.xml";

	/**
	 * Name of the widow displaying video stream.
	 */
	private final String CAPTURE_WINDOW = "Human detection";



	/**
	 * Time between each capture in the capture loop, in milliseconds.
	 */
	private int FRAMERATE         =  100;

	/**
	 * Maximum time between two detections for them to be considered
	 * continuous.
	 */
	private int CTIME_BREAK       =  200;

	/**
	 * Minimum continuous detection time before signaling.
	 */
	private int MIN_CTIME_SIGNAL  = 1000;

	/**
	 * Minimum time between each signaling calls.
	 */
	private int MIN_TIME_RESIGNAL = 2000;



	/**
	 * Used for logging.
	 */
	private final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	/**
	 * Used for file naming.
	 */
	private final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_hhmmssSSS");



	/**
	 * Beginning of the current continuous detection.
	 */
	private LocalDateTime continuousDetectionStart;

	/**
	 * Time of the last detection.
	 */
	private LocalDateTime lastDetectionTime;

	/**
	 * Time of the last signaling.
	 */
	private LocalDateTime lastSignalingTime;
	

	/*
	 * Has to be loaded before instantiating frames.
	 */
	static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	/**
	 * User input scanner.
	 */
	private final Scanner SCANNER = new Scanner(System.in);

	/**
	 * OpenCV class in charge of video capture.
	 */
	private final VideoCapture CAPTURE = new VideoCapture(Videoio.CAP_DSHOW);

	/**
	 * OpenCV class in charge of storing the values of a frame.
	 */
	private final Mat FRAME = new Mat();

	/**
	 * OpenCV class in charge of transforming a frame to greyscale.
	 */
	private final Mat GRAY_FRAME = new Mat();

	/**
	 * OpenCV class in charge of displaying detected objects.
	 */
	private final MatOfRect DETECTION_FRAME = new MatOfRect();



	@Override
	public void run() {
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
    private void loadClassifier(final String path, final CascadeClassifier humanDetector) throws FileNotFoundException, IllegalArgumentException {
        if (!(new File(path)).exists()) throw new FileNotFoundException(String.format("File %s not found", path));
        if (!humanDetector.load(path))  throw new IllegalArgumentException(String.format("Couldn't load CascadeClassifier with %s", path));
        printMessage("INFO", "Resources", "Loaded CascadeClassifier with " + path);
    }

    /**
     * Loop doing the video capture and detection. HighGui.waitKey(FRAMERATE)
     * insures a minimum duration (FRAMERATE value) between two iterations.
     *
     * @param  humanDetector  the CascadeClassifier used for detection
     */
    private void captureLoop(final CascadeClassifier humanDetector) {
        // Window for displaying results
        HighGui.namedWindow(CAPTURE_WINDOW, HighGui.WINDOW_AUTOSIZE);



        // Initialize video capture from camera
        if (!CAPTURE.isOpened()) {
            printMessage("ERROR", "Capture", "Video capture is not open. Check that camera is not in use in another program or running instance of Java");

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
                    	if (FRAME_SAVING && (DET_FRAME_PATH != null)) {
	                    	String imagePath = String.format("%sframe_%s.jpg", DET_FRAME_PATH, FILE_TIME_FORMATTER.format(time));
	                    	savedImage = Imgcodecs.imwrite(imagePath, FRAME);
                    	}
                    }
                }



                // Logging information
                StringBuilder messageSb = new StringBuilder();
                String level = "[INFO]";
                if (signaling) {
                	if (FRAME_SAVING && savedImage) {
                		messageSb.append("[OK] Saved frame to project_path/" + DET_FRAME_PATH);
                	}
                	else if (!FRAME_SAVING && !savedImage) {
                		messageSb.append("[OK] Frame saving disabled");
                	}
                	else {
                		messageSb.append("[NOK] Failed to save frame to project_path/frame/\"");
                	}
                }
                else {
                	if (resetContinuous) {
                		messageSb.append("Continuous detection broken");
                	}
                	else if (!minContinuous) messageSb.append("Insufficient continuous detection");
                	else if (!minBeforeSignaling) messageSb.append("Insufficient delay since last signaling");
                }

                System.out.println(String.format(" %-7s  EVNT: %9s  |  TIME: %s  |  DIFF: %6dms  |  STRT: %s  |  CONT: %6dms  |  SGNL: %9s  |  MESG: %s",
                		level,
                		"Detection",
                		TIME_FORMATTER.format(time),
                		timeDifference,
                		(continuousDetectionStart == null ? "            " : TIME_FORMATTER.format(continuousDetectionStart)),
                		(continuousDetectionStart == null ? "            " : continuousDetectionStart.until(time, ChronoUnit.MILLIS)),
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
    private void exit(final int exitCode) {
    	// Releasing resources
    	if (SCANNER != null) SCANNER.close();
    	printMessage("INFO", "Resources", "Scanner closed");

    	if (CAPTURE != null) CAPTURE.release();
    	printMessage("INFO", "Capture", "Video capture stopped");

        if (FRAME != null) FRAME.release();
    	printMessage("INFO", "Resources", "Frame released");

        if (GRAY_FRAME != null) GRAY_FRAME.release();
    	printMessage("INFO", "Resources", "Gray frame released");

        if (DETECTION_FRAME != null) DETECTION_FRAME.release();
    	printMessage("INFO", "Resources", "Detection frame released");

        HighGui.destroyWindow(CAPTURE_WINDOW);
    	printMessage("INFO", "Resources", "Window destroyed");

    	printMessage("INFO", "Resources", "Exiting program");
    	System.exit(exitCode);
    }

    private void printMessage(final String level, final String event, final String message) {
    	System.out.println(String.format(" %-7s  EVNT: %9s  |  TIME: %s  |  MESG: %s",
        		level,
        		event,
        		TIME_FORMATTER.format(LocalDateTime.now()),
        		message));
    }

	@Override
	public boolean getFrameSaving() {
		return FRAME_SAVING;
	}

	@Override
	public void setFrameSaving(final boolean frameSaving) {
		FRAME_SAVING = frameSaving;
		
	}

	@Override
	public int getFramerate() {
		return FRAMERATE;
	}

	@Override
	public void setFramerate(final int framerate) {
		FRAMERATE = framerate;
		
	}

	@Override
	public int getCTimeBreak() {
		return CTIME_BREAK;
	}

	@Override
	public void setCTimeBreak(final int cTimeBreak) {
		CTIME_BREAK = cTimeBreak;
		
	}

	@Override
	public int getMinCTimeSignal() {
		return MIN_CTIME_SIGNAL; 
	}

	@Override
	public void setMinCTimeSignal(final int minCTimeSignal) {
		MIN_CTIME_SIGNAL = minCTimeSignal;
		
	}

	@Override
	public int getMinTimeResignal() {
		return MIN_TIME_RESIGNAL;
	}

	@Override
	public void setMinTimeResignal(final int minTimeResignal) {
		MIN_TIME_RESIGNAL = minTimeResignal;
	}
}