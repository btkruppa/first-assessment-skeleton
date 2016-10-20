package collection;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cooksys.assessment.model.Message;

/**
 * A collection designed to map connected users to a queue of messages that have
 * not yet been sent to the user.
 * 
 * @author Blake
 *
 */
public class UnsentMessagesCollection {
	private Logger log = LoggerFactory.getLogger(UnsentMessagesCollection.class);
	private ConcurrentMap<String, BlockingQueue<Message>> messagesCollection = new ConcurrentHashMap<String, BlockingQueue<Message>>();

	/**
	 * Adds a user with the given username to the collection, and initializing a
	 * new {@link LinkedBlockingQueue} for that user
	 * 
	 * @param username
	 */
	public void addUser(String username) {
		messagesCollection.put(username, new LinkedBlockingQueue<Message>());
	}

	/**
	 * Check to see if a given username is in the collection
	 * 
	 * @param username
	 * @return true if the username is already in our collection or false if the
	 *         username is not.
	 */
	public boolean containsUser(String username) {
		return messagesCollection.containsKey(username);
	}

	/**
	 * Adds a message to the queue of the recipient
	 * 
	 * @param recipient
	 * @param message
	 */
	public void addMessageToUserQueue(String recipient, Message message) {
		if (messagesCollection.containsKey(recipient)) {
			messagesCollection.get(recipient).add(message);
		}
	}

	/**
	 * Remove a given username from the collection.
	 * 
	 * @param username
	 */
	public void removeUserFromCollection(String username) {
		messagesCollection.remove(username);
	}

	/**
	 * Invoke the take() method on a given users {@link BlockingQueue}. This is
	 * a blocking method that will wait until a message is at the front of the
	 * queue before removing and returning it.
	 * 
	 * @param username
	 * @return Message at the front of the queue. Or a new empty message if an
	 *         error occurred.
	 */
	public Message takeFromUsersQueue(String username) {
		try {
			return messagesCollection.get(username).take();
		} catch (InterruptedException e) {
			log.error("Error occured trying to take information from " + username + "'s queue", e);
			return new Message();
		}
	}

	/**
	 * 
	 * @return a set containing all of the users in the collection
	 */
	public Set<String> getUsers() {
		return messagesCollection.keySet();
	}
}
