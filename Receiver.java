package wifi;
import rf.RF;
import java.io.PrintWriter;

public class Receiver implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	
	public Receiver(RF theRF, short ourMAC, PrintWriter output) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
	}
	
	@Override
	public void run() {
		// TODO good things

	}

}
