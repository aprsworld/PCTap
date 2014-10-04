import java.util.Iterator;
import java.util.Vector;

import com.focus_sw.fieldtalk.MbusMasterFunctions;
import com.focus_sw.fieldtalk.MbusRtuMasterProtocol;
import com.focus_sw.fieldtalk.MbusSerialMasterProtocol;
import com.focus_sw.fieldtalk.MbusTcpMasterProtocol;

public class Stream implements Runnable {
	/*
	[STREAM_0]
	 type=MBUS_SERIAL
	 port=COM6
	 speed=9600
	 nDevices=2
	 */	 
	
	public final static int STREAM_TYPE_DISABLED          =0;
	public final static int STREAM_TYPE_MBUS_SERIAL       =1;
	public final static int STREAM_TYPE_MBUS_TCP          =2;
	public final static int STREAM_TYPE_SOUNDMETER_CENTER =3;
	public final static int STREAM_TYPE_SYSTEM_COMMAND    =4;
	public final static int STREAM_TYPE_FTULTRASONIC      =5;

	public final static boolean debug=false;
	final static boolean stdOut = false;
	final static boolean stdErr = false;
	public String description;
	public int streamId;
	public int streamType;
	public String serialPort;
	public int serialSpeed;
	public String command;
	public Vector<Device> devices;

	/* different type data sources */
	public MbusMasterFunctions mbus;
	public SerialReaderSoundmeterCenter sm;
	public SerialReaderFTUltrasonic ftu;

	public String tcpHost;
	public int tcpPort;
	public Vector<DataListenerRSTap> listeners;

	
	public void queryNow() {

		/* don't even try if we don't have a data connection */
		if ( ! isOpen() )
			return;

		/* iterate through all of our available devices and query */
		Iterator<Device> it = devices.iterator();

		while (it.hasNext()) {
			Object o = it.next();
			
			DataRSTap rsData=null;
			
			if ( o instanceof DeviceModbusSlave ) {
				DeviceModbusSlave d = (DeviceModbusSlave) o; 
				if ( Device.DEV_TYPE_DISABLED == d.type )
					continue;

				rsData = d.queryNow(mbus);
				
				//if (stdOut) System.out.println(rsData.qBuff);
				
			} else if ( o instanceof DeviceSoundMeterCenter ) {
				DeviceSoundMeterCenter d = (DeviceSoundMeterCenter) o; 
				if ( Device.DEV_TYPE_DISABLED == d.type )
					continue;

				/* query in this thread, wait for response */
				d.queryNow(sm);
			} else if ( o instanceof DeviceFTUltrasonic ) {
				DeviceFTUltrasonic u = (DeviceFTUltrasonic) o; 
				if ( Device.DEV_TYPE_DISABLED == u.type )
					continue;

				/* query in this thread, wait for response */
				u.queryNow(ftu);
			} else if ( o instanceof DeviceSystemCommand ) {
				DeviceSystemCommand d = (DeviceSystemCommand) o; 
				if ( Device.DEV_TYPE_DISABLED == d.type )
					continue;

				/* query in this thread, wait for response */
				rsData = d.queryNow(command);
			} else {
				if (stdErr) System.err.println("# Stream.queryNow(" + description + ") device is unknown!");
				if (stdErr) System.err.flush();				
			}
						
			//if (stdOut) System.out.println(rsData);

			/* if data is available now, we send it to our listeners */
			if ( null == rsData )
				continue;

			for ( int i=0 ; i<listeners.size(); i++ ) {
				listeners.elementAt(i).RSTapDataReceived(rsData);
			}
		}		
	}

	public boolean close() {
		if ( STREAM_TYPE_MBUS_SERIAL == streamType || STREAM_TYPE_MBUS_TCP == streamType ) {
			if ( null == mbus )
				return false;

			try {
				mbus.closeProtocol();
			} catch ( Exception e ) {
				if (stdErr) System.err.println("# Error closing Modbus port " + description);
				if (stdErr) System.err.println(e);
			}

			return ! mbus.isOpen();
		}
		
		if ( STREAM_TYPE_SOUNDMETER_CENTER == streamType ) {
			if ( null == sm )
				return false;
			
			sm.close();
			return ! sm.isOpen();
		}

		if ( STREAM_TYPE_FTULTRASONIC == streamType ) {
			if ( null == ftu )
				return false;
			
			ftu.close();
			return ! ftu.isOpen();
		}
		
		if ( STREAM_TYPE_SYSTEM_COMMAND == streamType ) {
			/* kill our proccess? */
		}
		
		
		return false;
	}

	public boolean open() {
		if ( STREAM_TYPE_MBUS_SERIAL == streamType ) {
			mbus = new MbusRtuMasterProtocol();

			try {
				mbus.setTimeout(500);
				mbus.setRetryCnt(0); // Increase to 2 for poor links
				mbus.setPollDelay(0); // Increase if slave needs time between polls
				((MbusRtuMasterProtocol) mbus).openProtocol(serialPort, serialSpeed,	MbusSerialMasterProtocol.DATABITS_8, MbusSerialMasterProtocol.STOPBITS_2, MbusSerialMasterProtocol.PARITY_NONE);
			} catch ( Exception e ) {
				if (stdErr) System.err.println("# Caught exception while opening Modbus Serial to " + description);
				if (stdErr) System.err.println(e);
			}
		} else if ( STREAM_TYPE_MBUS_TCP == streamType ) {
			mbus = new MbusTcpMasterProtocol();

			try {
				if ( 0 != tcpPort )
					((MbusTcpMasterProtocol) mbus).setPort(tcpPort);
				if ( null != tcpHost )
					((MbusTcpMasterProtocol) mbus).openProtocol(tcpHost);

			} catch ( Exception e ) {
				if (stdErr) System.err.println("# Caught exception while opening Modbus TCP to " + description);
				if (stdErr) System.err.println(e);
			}
		} else if ( STREAM_TYPE_SOUNDMETER_CENTER == streamType ) {
			sm = new SerialReaderSoundmeterCenter(serialPort, serialSpeed);
			/* add our parent as the packetListener */
			for ( int i=0 ; i<listeners.size() ; i++ ) {
				sm.addPacketListener(listeners.elementAt(i));
			}
		} else if ( STREAM_TYPE_FTULTRASONIC == streamType ) {
			ftu = new SerialReaderFTUltrasonic(serialPort, serialSpeed);
			/* add our parent as the packetListener */
			for ( int i=0 ; i<listeners.size() ; i++ ) {
				ftu.addPacketListener(listeners.elementAt(i));
			}
		} else if ( STREAM_TYPE_SYSTEM_COMMAND == streamType ) {
			/* do we need to do anything? */
		}

		return isOpen();
	} 

	public void run() {
		queryNow();
	}

	public String toString() {
		String s="description: " + description + 
				" streamId: " + streamId + 
				" streamType: " + getStreamStringByValue(streamType);

		if ( STREAM_TYPE_MBUS_SERIAL == streamType ) {
			s += " serialPort: " + serialPort + " serialSpeed: " + serialSpeed;
		} else if ( STREAM_TYPE_MBUS_TCP == streamType ) {
			s +=  " tcpHost: " + tcpHost + " tcpPort: " + tcpPort;
		} else if ( STREAM_TYPE_SOUNDMETER_CENTER == streamType ) {
			s += " serialPort: " + serialPort + " serialSpeed: " + serialSpeed;
		} else if ( STREAM_TYPE_FTULTRASONIC == streamType ) {
			s += " serialPort: " + serialPort + " serialSpeed: " + serialSpeed;
		} else if ( STREAM_TYPE_SYSTEM_COMMAND == streamType ) {
			s += " command: " + command;
		}


		s += " with " + devices.size() + " devices";

		return s;
	}

	public void addDevice(Device d) {
		devices.add(d);
	}

	public Device getDevice(int n) {
		return devices.elementAt(n);
	}

	public static String getStreamStringByValue(int n) {
		switch (n) {
		case STREAM_TYPE_DISABLED:          return "STREAM_TYPE_DISABLED";
		case STREAM_TYPE_MBUS_SERIAL:       return "STREAM_TYPE_MBUS_SERIAL";
		case STREAM_TYPE_MBUS_TCP:          return "STREAM_TYPE_MBUS_TCP";
		case STREAM_TYPE_SOUNDMETER_CENTER: return "STREAM_TYPE_SOUNDMETER_CENTER";
		case STREAM_TYPE_SYSTEM_COMMAND:    return "STREAM_TYPE_SYSTEM_COMMAND";
		case STREAM_TYPE_FTULTRASONIC:      return "STREAM_TYPE_FTULTRASONIC";
		default:                            return "Unknown streamType of " + n;
		}
	}

	public static int getStreamTypeByName(String s) {
		s=s.toUpperCase();

		if ( 0==s.compareTo("STREAM_TYPE_DISABLED") ) 
			return STREAM_TYPE_DISABLED;
		if ( 0==s.compareTo("STREAM_TYPE_MBUS_SERIAL") ) 
			return STREAM_TYPE_MBUS_SERIAL;
		if ( 0==s.compareTo("STREAM_TYPE_MBUS_TCP") ) 
			return STREAM_TYPE_MBUS_TCP;
		if ( 0==s.compareTo("STREAM_TYPE_SOUNDMETER_CENTER") )
			return STREAM_TYPE_SOUNDMETER_CENTER;
		if ( 0==s.compareTo("STREAM_TYPE_FTULTRASONIC") )
			return STREAM_TYPE_FTULTRASONIC;
		if ( 0==s.compareTo("STREAM_TYPE_SYSTEM_COMMAND") ) 
			return STREAM_TYPE_SYSTEM_COMMAND;
		
		return STREAM_TYPE_DISABLED;
	}

	public void addListener(DataListenerRSTap l) {
		listeners.add(l);
	}

	public boolean isOpen() {
		if ( STREAM_TYPE_MBUS_SERIAL == streamType || STREAM_TYPE_MBUS_TCP == streamType) {
			if ( null == mbus )
				return false;

			return mbus.isOpen();	
		}
		if ( STREAM_TYPE_SOUNDMETER_CENTER == streamType ) {
			if ( null == sm )
				return false;

			return sm.isOpen();
		}
		if ( STREAM_TYPE_FTULTRASONIC == streamType ) {
			if ( null == ftu )
				return false;

			return ftu.isOpen();
		}		
		if ( STREAM_TYPE_SYSTEM_COMMAND == streamType ) {
			return true;
		}

		return false;
	}

	public Stream (String d, DataListenerRSTap l) {
		description=d;
		devices=new Vector<Device>();
		mbus=null;
		sm=null;
		listeners = new Vector<DataListenerRSTap>();
		listeners.add(l);
	}

}
