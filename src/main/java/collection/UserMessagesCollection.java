package collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class UserMessagesCollection {
	private Map<String, BlockingQueue<List<String>>> messagesCollection = new ConcurrentHashMap();

	public void addUserToCollection(String username) { 
		messagesCollection.put(username, new LinkedBlockingQueue<List<String>>());
	}

	public boolean containsUser(String username) {
		return messagesCollection.containsKey(username);
	}

	public void addMessageToUserQueue(String username, String command, String contents) {
		if (messagesCollection.containsKey(username)) {
			List<String> entry = new ArrayList<String>();
			entry.add(username);
			entry.add(command);
			entry.add(contents);
			messagesCollection.get(username).add(entry);
		}
	}

	public void removeUserFromCollection(String username) {
		messagesCollection.remove(username);
	}

	public List<String> removeFromUsersQueue(String username) {
		try {
			return messagesCollection.get(username).take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ArrayList<String>();
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
