package com.cooksys.assessment.server;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.collection.UnsentMessagesCollection;
import com.cooksys.assessment.misc.Jenky;
import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides a method for servicing a given users queue by waiting while the
 * queue is empty and sending messages to that user when the queue is not empty.
 * There is a maximum of 100 messages per second because some {@link Jenky}
 * business was going on without the cap.
 * 
 * @author Blake
 *
 */
public class ClientWriter implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientWriter.class);

	private PrintWriter writer;
	private String username;
	private ObjectMapper mapper = new ObjectMapper();

	// I have a question, would it be better to sent the reference to this whole
	// collection here or would it be better to send only a reference to the
	// particular users queue???
	private UnsentMessagesCollection userMessagesCollection;

	/**
	 * @param writer,
	 *            the PrintWriter connected to use users socket
	 * @param username
	 * @param userMessagesCollection
	 */
	public ClientWriter(PrintWriter writer, String username, UnsentMessagesCollection userMessagesCollection) {
		this.writer = writer;
		this.username = username;
		this.userMessagesCollection = userMessagesCollection;
	}

	/**
	 * Main method that endlessly waits for messages in a queue and sends
	 * messages to the user whenever an item is in his queue. If a message with
	 * the command "TERMINATE" enters this queue the cycle will end.
	 */
	@Override
	public void run() {
		try {
			log.info("Client Writer thread started for " + username);
			Message sendingMessage;
			String response = "";
			do {
				Thread.sleep(10);
				sendingMessage = userMessagesCollection.takeFromUsersQueue(username);
				response = mapper.writeValueAsString(sendingMessage);
				writer.write(response);
				writer.flush();
			} while (sendingMessage.getCommand() != "TERMINATE");
		} catch (JsonProcessingException e) {
			log.error("error on queue", e);
		} catch (InterruptedException e) {
			log.error("error with sleeping?!?!?!?", e);
		} finally {
			log.info(username + "'s writter thread has come to an end");
		}

	}
}
