package com.cooksys.assessment.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import collection.UnsentMessagesCollection;

public class ClientWritter implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientWritter.class);
	
	private PrintWriter writer;
	private String username;
	private ObjectMapper mapper = new ObjectMapper();
	private UnsentMessagesCollection userMessagesCollection;

	public ClientWritter(PrintWriter writer, String username, UnsentMessagesCollection userMessagesCollection) {
		this.writer = writer;
		this.username = username;
		this.userMessagesCollection = userMessagesCollection;
	}

	@Override
	public void run() {
		try {
			log.info("Client Writer thread started for " + username);
			Message sendingMessage;
			String response = "";
			do {
				// if this sleep is not here then we can potentially flush two
				// messages into 1 on the JavaScript client side
				Thread.sleep(10);
				sendingMessage = userMessagesCollection.removeFromUsersQueue(username);
				response = mapper.writeValueAsString(sendingMessage);
				writer.write(response);
				writer.flush();
			} while(sendingMessage.getCommand() != "TERMINATE");
		} catch (JsonProcessingException e) {
			log.error("error on queue", e);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			log.info(username + "'s writter thread has come to an end");
		}

	}
}
