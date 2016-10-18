package collection;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UserMessagesCollection {
	private static Map<String, Queue<String>> messagesCollection = new HashMap();

	public static Set<String> getKeys() {
		return messagesCollection.keySet();
	}

	public static void addUserToCollection(String username) {
		messagesCollection.put(username, new ConcurrentLinkedQueue<String>());
	}

	public static boolean containsUser(String username) {
		return messagesCollection.containsKey(username);
	}

	public static void addMessageToUserQueue(String username, String contents) {
		if (messagesCollection.containsKey(username)) {
			messagesCollection.get(username).add(contents);
		}
	}

	public static void removeUserFromCollection(String username) {
		messagesCollection.remove(username);
	}

	public static String removeFromUsersQueue(String username) {
		return messagesCollection.get(username).remove();
	}

	public static boolean isUserQueueEmpty(String username) {
		//make sure there is a user in the collecting before trying to check the queue
		if(messagesCollection.containsKey(username)){
			return messagesCollection.get(username).isEmpty();
		}
		return true;
	}

	public static int getUserArraySize(String username) {
		return messagesCollection.get(username).size();
	}
}
