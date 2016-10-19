package com.cooksys.assessment.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import collection.UnsentMessagesCollection;

/**
 * Handles a client on a specific socket. Implements runnable so it can handle a
 * client on it's own thread and should in fact be implemented using threads. If
 * a valid user is connected a {@link ClientWritter} object will be spawned on
 * another thread.
 *
 * @author Blake Kruppa
 *
 */
public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private UnsentMessagesCollection userMessagesCollection;
	private ExecutorService executor;
	private Socket socket;
	private PrintWriter writer;
	private String username;

	/**
	 * This is the constructor for our {@link ClientHandler} When this object is
	 * created it also creates a PrintWriter using the provided socket
	 * 
	 * @param socket
	 * @param executor
	 * @param userMessagesCollection
	 */
	public ClientHandler(Socket socket, ExecutorService executor, UnsentMessagesCollection userMessagesCollection) {
		super();
		this.socket = socket;
		this.executor = executor;
		this.userMessagesCollection = userMessagesCollection;
		try {
			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			log.error("Could not start writer to write to client", e);
		}

	}

	private synchronized void broadcast(Message message) {
		Set<String> userSet = userMessagesCollection.getUserList();
		for (String user : userSet) {
			userMessagesCollection.addMessageToUserQueue(user, message);
		}
	}

	private void clientWriterThread() {
		Future<?> done;
		ClientWritter clientWriter = new ClientWritter(writer, username, userMessagesCollection);
		done = executor.submit(clientWriter);
	}

	private void disconnectUser() {
		// if our user disconnects without sending a disconnect message then
		// we need to send a message to ClientWritter to Terminate
		userMessagesCollection.addMessageToUserQueue(username, new Message(username, "TERMINATE", "You have been terminated"));
		userMessagesCollection.removeUserFromCollection(username);
		broadcast(new Message(username, "disconnect", new Date().toString() + ": <" + username + "> has disconnected"));
		try {
			socket.close();
		} catch (IOException e1) {
			log.error("Someting went wrong:/", e1);
		} finally {
			log.info(username + " removed from sets and client handler has come to an end");
		}
	}

	private void serviceCommand(Message message) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			String responseContents;

			switch (message.getCommand()) {
			case "connect":
				if (userMessagesCollection.containsUser(message.getUsername())) {
					log.info(message.getUsername()
							+ " tried to connect but was declined because that user already exists");
					message.setContents("User with the username " + message.getUsername() + " already exists");
					writer.write(mapper.writeValueAsString(message));
					writer.flush();
					socket.close();
				} else {
					log.info("user <{}> connected", message.getUsername());
					responseContents = new Date().toString() + ": <" + message.getUsername() + "> has connected";
					userMessagesCollection.addUserToCollection(username);
					message.setContents(responseContents);
					clientWriterThread();
					broadcast(message);
				}
				break;
			case "disconnect":
				log.info("user <{}> disconnected", message.getUsername());
				disconnectUser();
				break;
			case "echo":
				log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());
				userMessagesCollection.addMessageToUserQueue(username, message);
				break;
			case "users":
				log.info("user <{}> requested users", message.getUsername());
				responseContents = new Date().toString() + ": currently connected users:\n";
				Set<String> userSet = userMessagesCollection.getUserList();
				for (String user : userSet) {
					responseContents += "<" + (user + ">\n");
				}
				userMessagesCollection.addMessageToUserQueue(username,
						new Message(username, message.getCommand(), responseContents));
				break;
			case "broadcast":
				log.info("user <{}> sent message <{}> to all users", message.getUsername(), message.getContents());
				broadcast(message);
				break;
			default:
				if (message.getCommand().charAt(0) == '@') {
					String recipient = message.getCommand().substring(1);
					if (!userMessagesCollection.containsUser(recipient)) {
						userMessagesCollection.addMessageToUserQueue(username, new Message(username,
								message.getCommand(), "user " + recipient + " does not exist, direct message failed"));
					} else {
						log.info("user <{}> sent message <{}> to <{}>", message.getUsername(), message.getContents(),
								recipient);
						userMessagesCollection.addMessageToUserQueue(recipient, message);
					}
				}
				break;
			}
		} catch (IOException e) {

		}

	}

	/**
	 * 
	 */
	public void run() {
		try {
			Message message;
			ObjectMapper mapper = new ObjectMapper();
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			while (!socket.isClosed()) {
				String raw = reader.readLine();
				message = mapper.readValue(raw, Message.class);
				username = message.getUsername();
				serviceCommand(message);
			}

		} catch (IOException e) {
			log.error("Something went wrong :/", e);
			disconnectUser();
		}
	}

}
