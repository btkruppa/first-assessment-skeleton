package com.cooksys.assessment.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import collection.UserMessagesCollection;

public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private static Set<String> userSet = new HashSet();
	// private static Map<String, Queue<String>> broadcast = new HashMap();

	private ObjectMapper mapper = new ObjectMapper();

	private Socket socket;

	private String username;
	private Message message;
	private String response;

	public ClientHandler(Socket socket) {
		super();
		this.socket = socket;
	}

	private String getTimeStamp() {
		return (new Date()).toString();
	}

	private synchronized void broadcast(String message) {
		for (String user : userSet) {
			UserMessagesCollection.addMessageToUserQueue(user, message);
		}
	}

	private void writeToClient() {

		try {
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			Message sendingMessage = new Message();
			// sendingMessage.setCommand("echo");
			while (!UserMessagesCollection.isUserQueueEmpty(username)) {
				// if this sleep is not here then we can potentially flush two
				// messages into 1 on the JavaScript client side
				Thread.sleep(10);
				sendingMessage.setContents(UserMessagesCollection.removeFromUsersQueue(username));
				response = mapper.writeValueAsString(sendingMessage);
				writer.write(response);
				writer.flush();
			}
		} catch (JsonProcessingException e) {
			log.error("error on queue", e);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void run() {
		try {

			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			String responseContents;

			while (!socket.isClosed()) {
				if (reader.ready()) {
					String raw = reader.readLine();

					message = mapper.readValue(raw, Message.class);
					username = message.getUsername();

					response = "";
					responseContents = "";

					switch (message.getCommand()) {
					case "connect":
						log.info("user <{}> connected", message.getUsername());
						userSet.add(message.getUsername());
						responseContents = getTimeStamp() + ": <" + message.getUsername() + "> has connected";
						
						UserMessagesCollection.addUserToCollection(username);
						UserMessagesCollection.addMessageToUserQueue(username, responseContents);
						break;
					case "disconnect":
						log.info("user <{}> disconnected", message.getUsername());
						this.socket.close();
						userSet.remove(message.getUsername());
						break;
					case "echo":
						log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());
						responseContents = getTimeStamp() + " <" + message.getUsername() + "> (echo): <"
								+ message.getContents() + ">";
						UserMessagesCollection.addMessageToUserQueue(username, responseContents);
						break;
					case "users":
						log.info("user <{}> requested users", message.getUsername());
						responseContents = getTimeStamp() + ": currently connected users\n";
						for (String user : userSet) {
							responseContents += (user + "\n");
						}
						UserMessagesCollection.addMessageToUserQueue(username, responseContents);
						break;
					case "direct message":
						log.info("user <{}> sent message <{}>", message.getUsername(), message.getContents());
						String[] splitted = message.getContents().split(" ");
						UserMessagesCollection.addMessageToUserQueue(splitted[0], getTimeStamp() + "<" + username + "> (whisper):" + message.getContents().substring(splitted[0].length()));
						break;
					case "broadcast":
						log.info("user <{}> sent message <{}> to all users", message.getUsername(),
								message.getContents());
						broadcast(getTimeStamp() + "<" + username + "> (all):" + message.getContents());
						break;
					}
				}
				writeToClient();
			}

		} catch (IOException e) {
			log.error("Something went wrong :/", e);
		}
	}

}
