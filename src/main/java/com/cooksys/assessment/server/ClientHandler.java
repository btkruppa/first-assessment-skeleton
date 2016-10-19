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

import collection.UserMessagesCollection;

public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private UserMessagesCollection userMessagesCollection;

	// executor service and future for our ClientWritter threads
	private ExecutorService executor;
	private Future<?> done;

	private ObjectMapper mapper = new ObjectMapper();

	private Socket socket;
	private PrintWriter writer;

	private String username;
	private Message message;

	public ClientHandler(Socket socket, ExecutorService executor, UserMessagesCollection userMessagesCollection) {
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

	private String getTimeStamp() {
		return (new Date()).toString();
	}

	private synchronized void broadcast(String command, String message) {
		Set<String> userSet = userMessagesCollection.getUserList();
		for (String user : userSet) {
			userMessagesCollection.addMessageToUserQueue(user, command, message);
		}
	}

	public void clientWriterThread() {
		ClientWritter clientWriter = new ClientWritter(writer, username, userMessagesCollection);
		done = executor.submit(clientWriter);
	}

	public void disconnectUser() {
		// if our user disconnects without sending a disconnect message then
		// we need to send a message to ClientWritter to Terminate
		userMessagesCollection.addMessageToUserQueue(username, "TERMINATE", "You have been terminated");
		userMessagesCollection.removeUserFromCollection(username);
		broadcast("disconnect", getTimeStamp() + ": <" + username + "> has disconnected");
		try {
			socket.close();
		} catch (IOException e1) {
			log.error("Someting went wrong:/", e1);
		} finally {
			log.info(username + " removed from sets and client handler has come to an end");
		}
	}

	public void run() {
		try {

			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			String responseContents;

			String response;

			while (!socket.isClosed()) {
				String raw = reader.readLine();

				message = mapper.readValue(raw, Message.class);
				username = message.getUsername();

				response = "";
				responseContents = "";

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
						responseContents = getTimeStamp() + ": <" + message.getUsername() + "> has connected";
						userMessagesCollection.addUserToCollection(username);
						clientWriterThread();
						broadcast(message.getCommand(), responseContents);
					}
					break;
				case "disconnect":
					log.info("user <{}> disconnected", message.getUsername());
					disconnectUser();
					break;
				case "echo":
					log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());
					responseContents = getTimeStamp() + " <" + message.getUsername() + "> (echo): <"
							+ message.getContents() + ">";
					userMessagesCollection.addMessageToUserQueue(username, message.getCommand(), responseContents);
					break;
				case "users":
					log.info("user <{}> requested users", message.getUsername());
					responseContents = getTimeStamp() + ": currently connected users\n";
					Set<String> userSet = userMessagesCollection.getUserList();
					for (String user : userSet) {
						responseContents += (user + "\n");
					}
					userMessagesCollection.addMessageToUserQueue(username, message.getCommand(), responseContents);
					break;
				case "direct message":
					String[] splitted = message.getContents().split(" ");
					if (!userMessagesCollection.containsUser(splitted[0])) {
						userMessagesCollection.addMessageToUserQueue(username, message.getCommand(), "user " + splitted[0] + " does not exist, direct message failed");
					} else {
						log.info("user <{}> sent message <{}> to <{}>", message.getUsername(),
								message.getContents().substring(splitted[0].length()), splitted[0]);
						userMessagesCollection.addMessageToUserQueue(splitted[0], message.getCommand(),
								getTimeStamp() + " <" + username + "> (whisper):"
										+ message.getContents().substring(splitted[0].length()));
					}
					break;
				case "broadcast":
					log.info("user <{}> sent message <{}> to all users", message.getUsername(), message.getContents());
					broadcast(message.getCommand(),
							getTimeStamp() + " <" + username + "> (all):" + message.getContents());
					break;
				}
			}

		} catch (IOException e) {
			log.error("Something went wrong :/", e);
			disconnectUser();
		}
	}

}
