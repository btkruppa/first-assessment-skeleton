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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import collection.UserMessagesCollection;

public class ClientHandler implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private static Set<String> userSet = new HashSet();
	
	//executor service and future for our ClientWritter threads
	private ExecutorService executor;
	private Future<?> done;

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
			UserMessagesCollection.addMessageToUserQueue(user, "broadcast", message);
		}
	}

	public void clientWriterThread(String command) {

		try {
			if (command == "start") {				
				executor = Executors.newCachedThreadPool();

				PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

				ClientWritter clientWriter = new ClientWritter(writer, username);

				done = executor.submit(clientWriter);
				
			}
			if (command == "stop") {
				try {
					//done.get();
					executor.shutdownNow();
					executor.shutdownNow();
					executor.awaitTermination(5, TimeUnit.SECONDS);
					log.info(username + "'s client writter terminated");
				} catch (InterruptedException e) {
					log.error("Something went wrong :/", e);
				}
			}

//			try {
//				done.get();
//				executor.shutdown();
//				executor.awaitTermination(5, TimeUnit.SECONDS);
//			} catch (InterruptedException | ExecutionException e) {
//				log.error("Something went wrong :/", e);
//			}

		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public void run() {
		try {

			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			String responseContents;

			while (!socket.isClosed()) {
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
					clientWriterThread("start");
					UserMessagesCollection.addMessageToUserQueue(username, message.getCommand(), responseContents);
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
					UserMessagesCollection.addMessageToUserQueue(username, message.getCommand(), responseContents);
					break;
				case "users":
					log.info("user <{}> requested users", message.getUsername());
					responseContents = getTimeStamp() + ": currently connected users\n";
					for (String user : userSet) {
						responseContents += (user + "\n");
					}
					UserMessagesCollection.addMessageToUserQueue(username, message.getCommand(), responseContents);
					break;
				case "direct message":
					log.info("user <{}> sent message <{}>", message.getUsername(), message.getContents());
					String[] splitted = message.getContents().split(" ");
					UserMessagesCollection.addMessageToUserQueue(splitted[0], message.getCommand(), getTimeStamp() + " <" + username
							+ "> (whisper):" + message.getContents().substring(splitted[0].length()));
					break;
				case "broadcast":
					log.info("user <{}> sent message <{}> to all users", message.getUsername(), message.getContents());
					broadcast(getTimeStamp() + " <" + username + "> (all):" + message.getContents());
					break;
				}
			}

		} catch (IOException e) {
			clientWriterThread("stop");
			userSet.remove(username);
			UserMessagesCollection.removeUserFromCollection(username);
			log.error("Something went wrong :/", e);
			log.info(username + " removed from sets and ending thread");
			try {
				socket.close();
			} catch (IOException e1) {
				log.error("Someting went wrong:/", e1);
			}
		} 
	}

}
