package collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.cooksys.assessment.model.Message;

public class UnsentMessagesCollection {
	private Map<String, BlockingQueue<Message>> messagesCollection = new ConcurrentHashMap();

	public void addUserToCollection(String username) { 
		messagesCollection.put(username, new LinkedBlockingQueue<Message>());
	}

	public boolean containsUser(String username) {
		return messagesCollection.containsKey(username);
	}

	public void addMessageToUserQueue(String recipient, Message message) {
		if (messagesCollection.containsKey(recipient)) {
			messagesCollection.get(recipient).add(message);
		}
	}

	public void removeUserFromCollection(String username) {
		messagesCollection.remove(username);
	}

	public Message removeFromUsersQueue(String username) {
		try {
			return messagesCollection.get(username).take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new Message();
		}
	}

	public boolean isUserQueueEmpty(String username) {
		// make sure there is a user in the collecting before trying to check
		// the queue
		if (messagesCollection.containsKey(username)) {
			return messagesCollection.get(username).isEmpty();
		}
		return true;
	}

	public int getUserArraySize(String username) {
		return messagesCollection.get(username).size();
	}

	public Set<String> getUserList() {
		return messagesCollection.keySet();
	}
}
