import java.io.IOException;
import java.util.*;
//import gnu.io.*;
import javax.comm.*;

public class SerialReaderFTUltrasonic implements SerialPortEventListener {
	private LinkSerial link;
	private Boolean connected;
	protected Vector<DataListenerRSTap> packetListeners;

	private Vector<Integer> buff;
	private long lastCharacter;

	static boolean debug=false;
	protected DataRSTap packet;

	double wsMS, wgMS;
	int wdDegrees, temperatureC;
	boolean parsedSpeed, parsedGust, parsedTemperature;


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

	public void sendPacket(String s) {
		byte[] ba=new byte[s.length()+2]; // +2 for 0x0d 0x0a

		for ( int i=0 ; i<s.length() ; i++ )
			ba[i]=(byte) (s.charAt(i) & 0xff);

		/* temperature with 0x0d 0x0a as needed for the FT Ultrasonic to process the command */
		ba[ba.length-2]=0x0d;
		ba[ba.length-1]=0x0a;

		System.err.println("# sending sendPacket '" + s + "'");
		
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
		if ( debug )
			System.err.println("# capturedPacket (buff.size()=" + buff.size() + ")");

		/* convert the buffer into a string */
		StringBuilder sb=new StringBuilder(buff.size());
		for ( int i=0 ; i<buff.size() && 0x0d != buff.elementAt(i) && 0x0a != buff.elementAt(i); i++ ) {
			sb.append( (char) buff.elementAt(i).intValue() );
		}

		/* clear reader buffer for next pass through */
		buff.clear();

		/* we got a valid checksum packet */
		if ( ! DeviceFTUltrasonic.checkChecksum(sb.toString()) ) {
			if ( debug ) 
				System.err.println("# SerialReaderFTUltrasonic capturePacket() got an invalid checksum");

			/* we could set an exception */
			return;
		}

		/* determine what type of packet we have so we can process accordingly */
		String[] parts=sb.toString().split(",");

		/* WVP current wind direction sentence */
		if ( parts.length==4 && 0==parts[1].substring(0,3).compareTo("WVP") ) {
			if ( debug )
				System.err.println("# WVP (wind current) sentence");

			char status='-';

			try {
				wsMS = Double.parseDouble(parts[1].substring(4));
				wdDegrees = Integer.parseInt(parts[2]);
				status = parts[3].charAt(0);
			} catch ( Exception e ) {
				System.err.println("# Exception in SerialReaderFTUltrasonic capturePacket() while parsing WVP.\n" + e);
			}

			/* package into DataRSTap packet */
			if ( '0' == status ) {
				parsedSpeed=true;

				/* still need to get gust and temeprature */
				parsedGust=false;
				parsedTemperature=false;
			} else {
				parsedSpeed=false;

				parsedGust=false;
				parsedTemperature=false;
			}
		} else if ( parts.length==3 && 0==parts[1].substring(0,3).compareTo("MM=") ) {
			if ( debug )
				System.err.println("# MM (wind gust) sentence");

			/* parts[1] is minimum speed ... we don't really care about that */

			try {
				wgMS = Double.parseDouble(parts[2].substring(0,5));

				if ( parsedSpeed ) {
					parsedGust=true;
				} else {
					/* shouldn't get this first ... try again next time */
					parsedSpeed=false;
					parsedGust=false;
					parsedTemperature=false;
				}
			} catch ( Exception e ) {
				System.err.println("# Exception in SerialReaderFTUltrasonic capturePacket() while parsing MM.\n" + e);
			}	
		} else if ( parts.length==4 && 0==parts[1].substring(0,3).compareTo("HT=") ) {
			if ( debug )
				System.err.println("# HT (heater / temperature) sentence");	

			/*
			# parts[0]='$WI'
			# parts[1]='HT=99'
			# parts[2]='00'
			# parts[3]='+26*3C' current temperature 
			 */	

			try {
				int start=0;
				if ( '+' == parts[3].charAt(0) )
					start=1;

				temperatureC = Integer.parseInt(parts[3].substring(start,parts[3].indexOf('*')));

				if ( parsedSpeed && parsedGust ) {
					parsedTemperature=true;
				} else {
					/* shouldn't get this first ... try again next time */
					parsedSpeed=false;
					parsedGust=false;
					parsedTemperature=false;
				}
			} catch ( Exception e ) {
				System.err.println("# Exception in SerialReaderFTUltrasonic capturePacket() while parsing HT.\n" + e);
			}
		} else {
			if ( debug )
				System.err.println("# captured packet sb='" + sb + '"');

			System.err.println("# Unknown SerialReaderFTUltrasonic sentence");
			for ( int i=0 ; i<parts.length ; i++ ) {
				System.err.printf("# parts[%d]='%s'\n",i,parts[i]);
			}	

		}


		/* if we have all the sentences we need, we assemble into a DataRSTap packet and send */
		if ( ! parsedSpeed || ! parsedGust || ! parsedTemperature ) {
			return;
		}

		System.err.printf("# SerialReaderFTUltrasonic got three sentences. wsMS=%f wgMS=%f wdDegrees=%d temperatureC=%d\n", 
				wsMS,
				wgMS,
				wdDegrees,
				temperatureC
				);

		/* build our DataRSTap */

		/* valid packet, no exception */
		packet.qResult=DataRSTap.EXCEPTION_NONE;

		
		packet.qBuff=new int[8];
		
		int j;
		
		/* ws */
		j = (int) (wsMS * 10.0);
		packet.qBuff[0]=((j>>8)&0xff);
		packet.qBuff[1]=(j&0xff);

		/* wg */
		j = (int) (wgMS * 10.0);
		packet.qBuff[2]=((j>>8)&0xff);
		packet.qBuff[3]=(j&0xff);
		
		/* wind direction */
		packet.qBuff[4]=((wdDegrees<<8)&0xff);
		packet.qBuff[5]=(wdDegrees&0xff);
		
		/* temperature */
		short s = (short) (temperatureC * 10);
		
		System.err.printf("s = 0x%x\n",s);
		
		packet.qBuff[6]=((s>>8)&0xff);
		packet.qBuff[7]=(s&0xff);
		
		System.err.printf("temperaure: %d packet.qBuff[6]=0x%02x packet.qBuff[7]=0x%02x\n",s,packet.qBuff[6],packet.qBuff[7]);


		/* send our RSTapData packet to our listener(s) */
		for ( int i=0 ; i<packetListeners.size(); i++ ) {
				packetListeners.elementAt(i).RSTapDataReceived(packet);
		}
	}



	private void addChar(int c) {

		long now=System.currentTimeMillis();
		long age=now - lastCharacter;

		//		System.err.printf("# rx'ed: 0x%02X age: " + age + "\n",c);

		if ( buff.size() > 0 && age > 250 ) {
			System.err.println("# SerialReaderFTUltrasonic clearing buffer (length=" + buff.size() + ")");

			for ( int i=0 ; i<buff.size() ; i++ ) {
				System.err.printf("clearing [%d] 0x%02x %c\n",i,buff.elementAt(i),buff.elementAt(i));
			}

			buff.clear();
		}
		lastCharacter=now;

		//				System.err.printf("# rx'ed: %03d 0x%02x (buff.size()=%d age=%d)\n", c, c, buff.size(),age);
		//				System.err.flush();


		/* 0x0d character terminates packet ... I guess */
		if ( 0x0a == c ) {
			capturedPacket();
		} else {
			buff.add(c);
		}

	}


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


	public SerialReaderFTUltrasonic(String spName, int spSpeed) {
		buff = new Vector<Integer>();
		packetListeners = new Vector<DataListenerRSTap>();
		lastCharacter=0;

		link = new LinkSerial(spName,spSpeed);

		System.err.println("# SerialReaderFTUltrasonic(" + spName + ", " + spSpeed + ") created");

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


		parsedSpeed=parsedGust=parsedTemperature=false;
	}

	public void close() {
		link.Disconnect();
		connected=false;
	}

	public boolean isOpen() {
		return connected;
	}
}
