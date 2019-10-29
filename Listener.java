import rf.RF;

/**
 * A thread that listens for MAC addresses and timestamps sent over the RF layer.
 * 
 * @author Adam Braude
 */
public class Listener implements Runnable {

	private RF rf;
	private static final int expectedSize = 10;
	
	public Listener(RF rf) {
		this.rf = rf;
	}
	
	@Override
	public void run() {
		System.out.println("Listener's ears are open.");

		while (true) {
			byte[] incoming = rf.receive();
			if (incoming.length != expectedSize) {
				System.out.println("Error: packet size is " + incoming.length + " instead of expected " + expectedSize);
			} else {
				System.out.println("Received: " + TimeServer.bytesToString(incoming));
				int bigMac = (int)incoming[0];
				if (bigMac < 0) bigMac += 256;
				int littleMac = (int)incoming[1];
				if (littleMac < 0) littleMac += 256;
				int mac = (bigMac<<8) + littleMac;
				long time = 0;
				for (int i = 0; i < Long.BYTES; i++) {
					long longInst = ((long)incoming[incoming.length - i - 1]);
					if (longInst < 0) longInst += 256;
		        	time += longInst<<(8*i);
		        }
				System.out.println("Host " + mac + " says time is " + time);
			}
		}
	}

}
