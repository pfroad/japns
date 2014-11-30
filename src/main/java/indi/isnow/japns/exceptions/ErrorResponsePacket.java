package indi.isnow.japns.exceptions;


public class ErrorResponsePacket {
	private int command;
	private int statusCode;
	private Integer identifier;
	
	public ErrorResponsePacket(int command, int statusCode, Integer identifier) {
		this.command = command;
		this.statusCode = statusCode;
		this.identifier = identifier;
	}
	
	public int getCommand() {
		return command;
	}
	public void setCommand(int command) {
		this.command = command;
	}
	public int getStatusCode() {
		return statusCode;
	}
	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}
	public Integer getIdentifier() {
		return identifier;
	}
	public void setIdentifier(Integer identifier) {
		this.identifier = identifier;
	}
	
}
