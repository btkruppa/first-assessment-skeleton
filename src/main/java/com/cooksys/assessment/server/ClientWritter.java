package com.cooksys.assessment.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import collection.UserMessagesCollection;

public class ClientWritter implements Runnable {
	private Logger log = LoggerFactory.getLogger(ClientWritter.class);
	
	private PrintWriter writer;
	private String username;
	private ObjectMapper mapper = new ObjectMapper();

	public ClientWritter(PrintWriter writer, String username) {
		this.writer = writer;
		this.username = username;
	}

	@Override
	public void run() {
		try {
			log.info("Client Writer thread started for " + username);
			Message sendingMessage = new Message();
			String response = "";
			List<String> entry = new ArrayList<String>();
			while (true) {
				// if this sleep is not here then we can potentially flush two
				// messages into 1 on the JavaScript client side
				Thread.sleep(10);
				entry = UserMessagesCollection.removeFromUsersQueue(username);
				sendingMessage.setUsername(entry.get(0));
				sendingMessage.setCommand(entry.get(1));
				sendingMessage.setContents(entry.get(2));
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
}
