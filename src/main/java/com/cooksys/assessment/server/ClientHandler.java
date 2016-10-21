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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.collection.UnsentMessagesCollection;
import com.cooksys.assessment.misc.Shenanigans;
import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles a client on a specific socket. Implements runnable so it can handle a
 * client on it's own thread and should in fact be implemented using threads. If
 * a valid user is connected a {@link ClientWriter} object will be spawned on
 * another thread.
 *
 * @author Blake Kruppa
 *
 */
public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private UnsentMessagesCollection unsentMessagesCollection;
	private ExecutorService executor;
	private Socket socket;
	private PrintWriter writer;
	private String username;

	// since a connect message must be sent containing a valid username, we want
	// to decline any other message processing until the user connects with a
	// valid username
	private boolean isConnected = false;

	/**
	 * Constructor for our {@link ClientHandler}. When this object is created it
	 * also creates a PrintWriter using the provided socket.
	 * 
	 * @param socket
	 *            connected to a client
	 * @param executor
	 *            service being used to handle threads
	 * @param {@link
	 * 			UnsentMessagesCollection}
	 */
	public ClientHandler(Socket socket, ExecutorService executor, UnsentMessagesCollection userMessagesCollection) {
		super();
		this.socket = socket;
		this.executor = executor;
		this.unsentMessagesCollection = userMessagesCollection;
		try {
			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
		} catch (IOException e) {
			log.error("Could not initialize PrintWriter to write to client", e);
		}
	}

	/**
	 * Synchronized method for broadcasting a message to all users that ensures
	 * broadcasts are always sent in the same order. Note if you want to improve
	 * this in the case of very large numbers of connected users, you can have
	 * this function push to an intermediate broadcast queue that is processed
	 * by another thread.
	 * 
	 * @param message
	 *            to be broadcast to all users
	 */
	private synchronized void broadcast(Message message) {
		Set<String> userSet = unsentMessagesCollection.getUsers();
		for (String user : userSet) {
			unsentMessagesCollection.addMessageToUserQueue(user, message);
		}
	}

	/**
	 * If either a user sends the command to disconnect or a connection issue
	 * occurs this method can be called. The user will then be removed from the
	 * {@link UnsentMessagesCollection} and a message will be broadcast to all
	 * other users notifying of the disconnect.
	 */
	private void disconnectUser() {
		// if our user disconnects without sending a disconnect message then
		// we need to send a message to ClientWritter to Terminate
		unsentMessagesCollection.addMessageToUserQueue(username,
				new Message(username, "TERMINATE", "You have been terminated"));
		unsentMessagesCollection.removeUserFromCollection(username);
		broadcast(new Message(username, "disconnect", new Date().toString() + ": <" + username + "> has disconnected"));
		try {
			socket.close();
		} catch (IOException e1) {
			log.error("Someting went wrong:/", e1);
		} finally {
			log.info(username + " removed from sets");
		}
	}

	/**
	 * Function to connect a user the first time a command to connect with a
	 * valid username is made. Connecting the user includes assigning his
	 * username to this {@link ClientHandler}, initializing the user in the
	 * {@link UnsentMessagesCollection}, starting a new thread to service his
	 * queue in collection, and broadcasting to all other users that he has
	 * connected.
	 * 
	 * @param message
	 */
	private void connectUser(Message message) {
		log.info("user <{}> connected", message.getUsername());
		username = message.getUsername();
		executor.submit(new ClientWriter(writer, username, unsentMessagesCollection));
		// add the user to our userMessagesCollection
		unsentMessagesCollection.addUser(username);

		// broadcast that the user has connected
		message.setContents(new Date().toString() + ": <" + message.getUsername() + "> has connected");
		broadcast(message);
		isConnected = true;
	}

	/**
	 * Takes a messaged received from a client and processes it based on the
	 * command. Note all clients are required to connect with a connect command
	 * so until this is received no messages will be written back to the user.
	 * Also once connected messages received from this client must match the
	 * username connected with to ensure no {@link Shenanigans} are going on.
	 * 
	 * @param received
	 *            message to be processed
	 */
	private void serviceCommand(Message message) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			String responseContents;
			// check connection state before processing commands
			if (!isConnected) {
				if (message.getCommand().equals("connect")) {
					// do not start the connecting process if the username is
					// already in use
					if (unsentMessagesCollection.containsUser(message.getUsername())) {
						log.info(message.getUsername()
								+ " tried to connect but was declined because that user already exists");
						message.setContents("User with the username " + message.getUsername() + " already exists");
						writer.write(mapper.writeValueAsString(message));
						writer.flush();
						socket.close();
					} else {
						connectUser(message);
					}
				}
			} else if (message.getUsername().equals(username)) {
				// ^check the username of the sent message to the username for
				// this client handler to insure that no shenanigans are going
				// on after a client has connected
				switch (message.getCommand()) {
				case "disconnect":
					log.info("user <{}> disconnected", message.getUsername());
					disconnectUser();
					break;
				case "echo":
					log.info("user <{}> echoed message <{}>", message.getUsername(), message.getContents());
					unsentMessagesCollection.addMessageToUserQueue(username, message);
					break;
				case "users":
					log.info("user <{}> requested users", message.getUsername());
					responseContents = new Date().toString() + ": currently connected users:\n";
					Set<String> userSet = unsentMessagesCollection.getUsers();
					for (String user : userSet) {
						responseContents += "<" + (user + ">\n");
					}
					unsentMessagesCollection.addMessageToUserQueue(username,
							new Message(username, message.getCommand(), responseContents));
					break;
				case "broadcast":
					if(unsentMessagesCollection.getUsers().size() != 1){
					log.info("user <{}> sent message <{}> to all users", message.getUsername(), message.getContents());
					broadcast(message);
					} else {
						log.info("user <{}> likes broadcasting to himself", username);
						message.setContents("You do realize that you are the only one in here right?");
						unsentMessagesCollection.addMessageToUserQueue(username, message);
					}
					break;
				default:
					// The switch statement improves readability but it cannot
					// properly process direct message command as it's own case,
					// so it is included in the default case
					if (message.getCommand().charAt(0) == '@') {
						String recipient = message.getCommand().substring(1);
						if (!unsentMessagesCollection.containsUser(recipient)) {
							message.setContents("user " + recipient + " does not exist, direct message failed");
							unsentMessagesCollection.addMessageToUserQueue(username, message);
						} else {
							if (username.equals(recipient)) {
								// Because why are you talking to yourself?
								log.info("user <{}> is an odd fellow", username);
								message.setContents("Why are you whispering to yourself?");
								unsentMessagesCollection.addMessageToUserQueue(username, message);
							} else {
								log.info("user <{}> sent message <{}> to <{}>", message.getUsername(),
										message.getContents(), recipient);
								unsentMessagesCollection.addMessageToUserQueue(recipient, message);
								unsentMessagesCollection.addMessageToUserQueue(username, new Message(username, message.getCommand(), "Message Sent to " + recipient));
							}
						}
					} else {
						// real default after all cases have been checked
						message.setContents("Command " + message.getCommand() + " unrecognized by the server");
						unsentMessagesCollection.addMessageToUserQueue(username, message);
					}
					break;
				}// command case
			} // connection check
		} catch (IOException e) {
			log.error("error processing command", e);
		}
	}

	/**
	 * Main method of client handler that controls the flow of reading and
	 * writing information for a given user's socket
	 */
	public void run() {
		try {
			Message message;
			ObjectMapper mapper = new ObjectMapper();
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String raw;
			while (!socket.isClosed()) {
				raw = reader.readLine();
				message = mapper.readValue(raw, Message.class);
				serviceCommand(message);
			}

		} catch (IOException e) {
			log.error("Something went wrong, disconnecting user :/", e);
			disconnectUser();
		} finally {
			log.info(username + "'s client handler has come to an end");
		}
	}
}
