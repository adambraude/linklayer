package wifi;
import rf.RF;
import java.io.PrintWriter;

public class Sender implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	
	public Sender(RF theRF, short ourMAC, PrintWriter output) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
	}
	
	@Override
	public void run() {
		// TODO do useful stuff

	}

}
