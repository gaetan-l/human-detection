package com.gaetanl.videocapture;

public interface HumanDetectionMBean {
	public boolean getFrameSaving();
	public void setFrameSaving(final boolean frameSaving);

	public int getFramerate();
	public void setFramerate(final int framerate);

	public int getCTimeBreak();
	public void setCTimeBreak(final int cTimeBreak);

	public int getMinCTimeSignal();
	public void setMinCTimeSignal(final int minCTimeSignal);

	public int getMinTimeResignal();
	public void setMinTimeResignal(final int minTimeResignal);
}