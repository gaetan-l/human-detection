# Setup
## Installation of OpenCV
- Link: https://opencv.org/releases/
- Version used: 4.9.0
- Install downoaded OpenCV
## Set up OpenCV for Java in Eclipse
- Link: https://opencv-java-tutorials.readthedocs.io/en/latest/01-installing-opencv-for-java.html
- Open Eclipse and select a workspace of your choice. Create a User Library, ready to be used on all your next projects: go to Window > Preferences....
- From the menu navigate under Java > Build Path > User Libraries and choose New.... Enter a name for the library (e.g., opencv) and select the newly created user library. Choose Add External JARs..., browse to select opencv-3xx.jar from your computer. After adding the jar, extend it, select Native library location and press Edit....
- Select External Folder... and browse to select the folder containing the OpenCV libraries (e.g., C:\opencv\build\java\x64 under Windows).
## Add the User Library to Eclipse project
- Link: https://opencv-java-tutorials.readthedocs.io/en/latest/02-first-java-application-with-opencv.html
- If you followed the previous tutorial (Installing OpenCV for Java), you should already have the OpenCV library set in your workspace’s user libraries; if not please check out the previous tutorial. Now you should be ready to add the library to your project. Inside Eclipse’s Package Explorer just right-click on your project’s folder and go to Build Path --> Add Libraries....
- Select User Libraries and click on Next, check the checkbox of the OpenCV library and click Finish.
## Add more Haar Cascade Classifiers to /resources if needed
- Link: https://github.com/opencv/opencv/tree/master/data/haarcascades
