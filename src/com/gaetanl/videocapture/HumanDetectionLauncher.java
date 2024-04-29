package com.gaetanl.videocapture;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class HumanDetectionLauncher {
	public static void main(String[] args) throws Exception {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer(); 
        ObjectName name = new ObjectName("com.gaetanl.videocapture:type=HumanDetection"); 
        HumanDetection humanDetection = new HumanDetection(); 
        mbs.registerMBean(humanDetection, name); 

        humanDetection.run();
	}
}