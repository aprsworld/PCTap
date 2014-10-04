import java.io.IOException;
import java.util.*;
//import gnu.io.*;
import javax.comm.*;

public class SerialReaderSoundmeterCenter implements SerialPortEventListener {
	private LinkSerial link;
	private Boolean connected;
	protected Vector<DataListenerRSTap> packetListeners;

	private Vector<Integer> buff;
	private long lastCharacter;

	static boolean debug=false;
	protected DataRSTap packet;

	public void sendPacket(int buff[]) {
		byte[] ba=new byte[buff.length];

		for ( int i=0 ; i<buff.length ; i++ )
			ba[i]=(byte) (buff[i] & 0xff);

		try {
			link.os.write(ba);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public void addPacketListener(DataListenerRSTap b) {
		packetListeners.add(b);
	}

	public void setDataRSTap(DataRSTap d) {
		packet=d;
	}
	public DataRSTap getDataRSTap() {
		return packet;
	}
	

	protected void capturedPacket() {
//		System.err.println("# capturedPacket (buff.size()=" + buff.size() + ")");
	

		/* copy our received data to RSTap packet */
		packet.qResult=DataRSTap.EXCEPTION_NONE;
		packet.qBuff=new int[buff.size()];
		for ( int i=0 ; i<buff.size() ; i++ )
			packet.qBuff[i]=buff.elementAt(i);

		/* clear reader buffer for next pass through */
		buff.clear();
		
		if ( debug ) {
			for ( int i=0 ; i<packet.qBuff.length ; i++ ) {
				System.err.printf(" qBuff[%d] = 0x%02x\n",i,packet.qBuff[i]);
			}
		}
		
		
		for ( int i=0 ; i<packetListeners.size(); i++ ) {
//			System.err.println("# sent to listener: " + i);
			packetListeners.elementAt(i).RSTapDataReceived(packet);
		}
	}


	
	private void addChar(int c) {

		long now=System.currentTimeMillis();
		long age=now - lastCharacter;

		//		System.err.printf("# rx'ed: 0x%02X age: " + age + "\n",c);

		if ( buff.size() > 0 && age > 250 ) {
			System.err.println("# SerialReaderSoundmeterCenter clearing buffer (length=" + buff.size() + ")");
			buff.clear();
		}
		lastCharacter=now;

		//				System.err.printf("# rx'ed: %03d 0x%02x (buff.size()=%d age=%d)\n", c, c, buff.size(),age);
		//				System.err.flush();

		
		buff.add(c);

		if ( 15 == buff.size() )
			capturedPacket();

	}

	
	/* capture a line and send it to capturedBarcode */
	public void serialEvent(SerialPortEvent event) {
		if ( SerialPortEvent.DATA_AVAILABLE == event.getEventType() ) {
			try { 
				while ( link.is.available() > 0 ) {
					int c=0;
					try { 
						c = link.is.read();
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
					addChar(c);
				}
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}

	}


	public SerialReaderSoundmeterCenter(String spName, int spSpeed) {
		buff = new Vector<Integer>();
		packetListeners = new Vector<DataListenerRSTap>();
		lastCharacter=0;

		link = new LinkSerial(spName,spSpeed);

		if ( null == link || false == link.Connect()) {
			System.err.println("# Error establishing serial link to device");
			connected=false;
			return;
		}
		connected=true;

		try {
			link.p.addEventListener(this);
		} catch ( TooManyListenersException e ) {
			System.err.println("# Serial port only supports one SerialPortEventListener!");
		}

		link.p.notifyOnDataAvailable(true);
	}

	public void close() {
		link.Disconnect();
		connected=false;
	}
	
	public boolean isOpen() {
		return connected;
	}
}
