package indi.isnow.japns.notification;

import indi.isnow.japns.util.Utilities;

import java.util.List;

public class ApnsNotificationFrame {
	private final static byte COMMAND = 2;
	List<ApnsNotification> frameDatas;
	public ApnsNotificationFrame(List<ApnsNotification> frameDatas) {
		super();
		this.frameDatas = frameDatas;
	}
	
	private byte[] marshall = null;
	
	public byte[] marshall(){
		if (marshall == null) {
			marshall = Utilities.marshallFrame(COMMAND, frameDatas);
		}
		return marshall.clone();
	}
}
