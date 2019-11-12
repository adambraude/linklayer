package wifi;

import java.util.zip.CRC32;

public class Packet {

	public static final int MAX_DATA = 2038;
	public static final int PACKET_SIZE = 2048;
	
	public static final int FT_DATA = 0;
	public static final int FT_ACK = 1;
	public static final int FT_BEACON = 2;
	public static final int FT_CTS = 4;
	public static final int FT_RTS = 5;
	public static final int MAX_SEQ = 4095;
	public static final int MAX_BYTE = 256;
	public static final int NONDATABYTES=10;
	
	private byte[] packet;
	private byte[] data;
	
	/**
	 * Given appropriate info, build a packet
	 * @param src source address
	 * @param dest destination address
	 * @param data an array of data: maximum of 2038 bytes
	 * @param type one of the FT_x constants associated with this class
	 * @param seq the sequence number (should be a 12-bit int)
	 * @param retry whether this is a retry
	 */
	public Packet (short src, short dest, byte [] data, int type, int seq,boolean retry) {
		if (data.length > MAX_DATA) {
			throw new IllegalArgumentException("Maximum packet length of " + MAX_DATA + " exceeded.");
		}
		while (seq > MAX_SEQ) seq -= MAX_SEQ;
		packet = new byte[data.length + NONDATABYTES];
		packet[0] = (byte)(seq >>8);
		packet[0] |= (type <<5);
		if (retry) packet[0] |= 1 << 4;
		packet[1] = (byte)(seq);
		packet[2] = (byte)(dest>>8);
		packet[3] = (byte)(dest);
		packet[4] = (byte)(src>>8);
		packet[5] = (byte)(src);
		System.arraycopy(data, 0, packet, 6, data.length);
		CRC32 chksm = new CRC32();
		chksm.update(packet, 0, packet.length-4);
		for (int i = 0; i < 4; i++) {
			packet[packet.length-1-i] = (byte)(chksm.getValue()>>8*i);
		}
	}
	
	/**
	 * Converts a received packet into a packet object
	 * @param received the packet received from a transmission
	 */
	public Packet (byte[] received) {
		packet = received;
	}
	
	/**
	 * Gets the type of the packet
	 * @return FT_DATA, FT_ACK, FT_CTS, FT_RTS, or FT_BEACON
	 */
	public int getType() {
		int type = packet[0]>>5;
		if (type < 0) type *=-1;
		return type;
	}
	
	/**
	 * Returns the sequence number
	 * @return the sequence number, which is at most 12 bits
	 */
	public short getSeq() {
		int big = ((byte)(packet[0]<<4)>>4)<<8;
		if (big < 0) big += MAX_SEQ+1;
		int little = (int)packet[1];
		if (little<0) little += MAX_BYTE;
		return (short) (little + big);
	}
	
	/**
	 * Check if the packet is a retry
	 * @return true if the packet is a retry
	 */
	public boolean getRetry() {
		int ret = (byte)(packet[0]<<3)>>7;
		if (ret == -1) return true;
		else return false;
	}
	
	//Helper method for pulling integers out of a byte array.
	private int bytesToInt(int start, int end) {
		int len = start-end;
		int total = 0;
		for (int i = 0; i <= end-start; i++) {
			int temp = (int)packet[start+i];
			if (temp <0) temp += MAX_BYTE;
			temp <<= (8*(end-start-i));
			total += temp;
		}
		return total;
	}
	
	/**
	 * Gets the source address
	 * @return a short with the source address
	 */
	public short getSrc() 
	{
		return (short)bytesToInt(4,5);
	}
	
	/**
	 * Gets the destination address
	 * @return a short with the destination address
	 */
	public short getDest() {
		return (short)bytesToInt(2,3);
	}

	/**
	 * An array buffer with all the data
	 * @return a byte array of length 2038
	 */
	public byte[] getData() {
		if (data == null) {
			data = new byte[packet.length-10];
			System.arraycopy(packet, 6, data, 0, packet.length-NONDATABYTES);
		}
		return data;
	}
	
	/**
	 * Returns the packet as a byte array
	 * @return an array of 2048 bytes laid out as per 802.11~
	 */
	public byte[] getPacket() {
		//This one is good
		return packet;
	}
	
	/**
	 * Verifies the integrity of the packet using the CRC
	 * @return true if the calculated checksum matches the one included with the packet.
	 */
	public boolean integrityCheck () {
		int crc = bytesToInt(packet.length-5,packet.length-1);
		CRC32 chksm = new CRC32();
		chksm.update(packet, 0, packet.length-4);
		return crc==chksm.getValue();
	}
	
	// Unit-testing
	public static void main(String[] args) {
		Packet p = new Packet((short)555,(short)222,new byte[50], FT_CTS, 4092, true);
		System.out.println("Is this a retry? " + p.getRetry());
		System.out.println("Seq?: " + p.getSeq() + " Expected: 4092");
		System.out.println("Type?: " + p.getType() + " Expected: " + FT_CTS);
		System.out.println("Dest: " + p.getDest());
		System.out.println("Src: " + p.getSrc());
		System.out.println("Checksum good? " + p.integrityCheck());
	}

}
