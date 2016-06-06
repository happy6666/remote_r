import javax.mail.MessagingException;
import javax.mail.Store;
import java.util.concurrent.TimeoutException;

/**
 * Created by yiwen on 6/6/16.
 */
public abstract class TimeoutConnect {
	private static class Connect implements Runnable {
		private Store store;
		private String host, username, password;

		public Connect(Store store, String host, String username, String password) {
			this.store = store;
			this.host = host;
			this.username = username;
			this.password = password;
		}

		@Override
		public void run() {
			try {
				store.connect(host, username, password);
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}
	}

	public static void timeoutConnect(Store store, String host, String username, String password, long timeout)
			throws TimeoutException {
		Thread t = new Thread(new Connect(store, host, username, password), "TimeoutConnection");
		long t0 = System.currentTimeMillis();
		while (true) {
			t.start();
			if (!t.isAlive()) {
				break;
			}
			if (System.currentTimeMillis() - t0 > timeout) {
				System.out.println("Connect store timeout");
				throw new TimeoutException("Connect store timeout");
			}
		}
	}
}
