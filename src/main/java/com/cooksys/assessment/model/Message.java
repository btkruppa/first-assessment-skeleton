package com.cooksys.assessment.model;

/**
 * A message is an object that contains a username of who sent it, a command to
 * process how it is sent, and contents of what to send. Message objects are the
 * only means of communication between server and client.
 * 
 * @author Peter
 *
 */
public class Message {

	private String username;
	private String command;
	private String contents;

	public Message() {
	}

	public Message(String username, String command, String contents) {
		this.username = username;
		this.command = command;
		this.contents = contents;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public String getContents() {
		return contents;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

}
