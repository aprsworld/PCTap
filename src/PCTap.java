import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Vector;
import java.util.Iterator;

import net.spy.memcached.MemcachedClient;


public class PCTap implements DataListenerRSTap, MemcacheListener {
	protected IniFile ini;
	protected Vector<Stream> streams;
	protected Vector<WorldData> worlds;
	protected Vector<WorldData> worldsToReconnect;

	final static boolean stdOut = true;
	final static boolean stdErr = true;


	protected Vector<Process> processes;

	private javax.swing.Timer heart;
	protected int serialPrefix, serialNumber;
	protected int measurementNumber;
	protected int queryInterval;

	/* Memcache client for logging */
	private static MemcachedClient memcache;
	protected int memcacheTimeout = 3600*24*2;

	public void memcacheWrite(String key, String val) {
		

		if ( null == memcache )
			return;

		/* add this value to memcache */			
		key = "PCTAP_" + key;
		key = key.toUpperCase();
		memcache.set(key,memcacheTimeout,val);

		System.out.println("# memcacheWrite stub key=" + key + " val=" + val);
	}


	/* get data from somebody in preparation to send out to WorldData */
	public void RSTapDataReceived(DataRSTap packet) {


		if ( DataRSTap.EXCEPTION_NONE != packet.qResult ) {
			if (stdErr) System.err.println("# got exception: " + DataRSTap.getExceptionByValue(packet.qResult));
		} else {
			byte worldPacket[]=packet.assemblePacket(serialPrefix, serialNumber,measurementNumber);


			/* send data to processors */
			Iterator<Process> itp = processes.iterator();
			while (itp.hasNext()) {
				Process p = itp.next();

				p.addData(packet);
			}

			/* send data to worlddata servers */			
			Iterator<WorldData> it = worlds.iterator();
			int i=0;
			while (it.hasNext()) {
				WorldData w = it.next();

				//				if (stdErr) System.err.println("# RSTapDataReceived has the following packet to send:\n" + w);

				boolean sendPacketStatus = w.sendPacket(worldPacket);

				if ( false == sendPacketStatus ) {
					if (stdErr) System.err.print("# Packet not successfully sent to " + w.toString());
					if ( ! worldsToReconnect.contains(w) ) {
						if (stdErr) System.err.println(" adding to re-connect list.");
						worldsToReconnect.add(w);
					} else {
						if (stdErr) System.err.println(" is already on re-connect list.");
					}
				}
				
				/* update memcache with the worldData's status */
				memcacheWrite("WORLDDATA_" + i + "_STATE", w.getConnectionState());
				
				i++;
			}
		}
	}

	protected void loadWorldDataConfig() {
		worlds = new Vector<WorldData>();
		worldsToReconnect = new Vector<WorldData>();

		if (stdErr) System.err.println("# Loading world data servers ... ");


		for ( int stream=0 ; stream<Integer.MAX_VALUE ; stream++ ) {
			String sName = "WORLDDATA_" + stream;

			if ( false == ini.hasSubject(sName) )
				break;


			String host=ini.getValueSafe(sName,"host","localhost");
			int port=Integer.valueOf(ini.getValueSafe(sName,"port","4010"));

			worlds.add(new WorldData(host,port));			
		}

		for ( int i=0 ; i<worlds.size() ; i++ ) {
			WorldData w=worlds.elementAt(i);
			if (stdErr) System.err.printf("# WORLDDATA_%d = %s\n",i,w);
			
			memcacheWrite("WORLDDATA_" + i + "_HOST", w.getHost());
			memcacheWrite("WORLDDATA_" + i + "_PORT", Integer.toString(w.getPort()));
			memcacheWrite("WORLDDATA_" + i + "_STATE", w.getConnectionState());
		}

	}

	protected void loadProcessConfig(int interval) {
		processes = new Vector<Process>();

		if (stdErr) System.err.println("# Loading control processes ... ");


		for ( int stream=0 ; stream<Integer.MAX_VALUE ; stream++ ) {
			String sName = "PROCESS_" + stream;

			if ( false == ini.hasSubject(sName) )
				break;

			String description = ini.getValueSafe(sName, "description", sName);

			processes.add(new Process(interval,description,sName,ini));			
		}

		for ( int i=0 ; i<processes.size() ; i++ ) {
			Process p=processes.elementAt(i);
			if (stdErr) System.err.printf("# PROCESS_%d = %s\n",i,p);
		}

	}

	protected void loadStreamsConfig() {

		streams = new Vector<Stream>();

		if (stdErr) System.err.println("# Loading devices ... ");


		for ( int stream=0 ; stream<Integer.MAX_VALUE ; stream++ ) {
			String sName = "STREAM_" + stream;

			if ( false == ini.hasSubject(sName) )
				break;

			/* create a new stream with this class as the data listener */
			Stream s=new Stream("STREAM_" + stream,this);
			s.streamId=stream;
			s.streamType=Stream.getStreamTypeByName(ini.getValueSafe(sName, "type", ""));


			s.serialPort=ini.getValueSafe(sName, "serialPort", "");
			s.serialSpeed=Integer.valueOf(ini.getValueSafe(sName,"serialSpeed","0"));

			s.tcpHost=ini.getValueSafe(sName,"tcpHost","localhost");
			s.tcpPort=Integer.valueOf(ini.getValueSafe(sName,"tcpPort","502"));

			s.command=ini.getValueSafe(sName,"systemCommand","default command");

			for ( int device=0 ; device<Integer.MAX_VALUE ; device++ ) {
				String dName = sName + "_DEVICE_" + device;

				if ( false == ini.hasSubject(dName) )
					break;

				if ( Device.DEV_TYPE_FTULTRASONIC == Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED")))  {
					DeviceFTUltrasonic dfu = new DeviceFTUltrasonic(dName, this);
					dfu.type=Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED"));
					dfu.typeWorld=Integer.valueOf(ini.getValueSafe(dName,"typeWorld","0"));;
					dfu.transmitEvery=1;
					dfu.serialNumber=Integer.valueOf(ini.getValueSafe(dName,"serialNumber","0"));;
					s.addDevice(dfu);					
				} else if ( Device.DEV_TYPE_SOUNDMETER_CENTER == Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED")))  {
					DeviceSoundMeterCenter dms = new DeviceSoundMeterCenter(dName,this);
					dms.type=Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED"));
					dms.typeWorld=Integer.valueOf(ini.getValueSafe(dName,"typeWorld","0"));;
					dms.transmitEvery=1;
					dms.serialNumber=Integer.valueOf(ini.getValueSafe(dName,"serialNumber","0"));;
					s.addDevice(dms);
				} else if ( Device.DEV_TYPE_SYSTEM_COMMAND == Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED")))  {
					DeviceSystemCommand dsc = new DeviceSystemCommand(dName,this);
					dsc.type=Device.DEV_TYPE_SYSTEM_COMMAND;
					dsc.typeWorld=Integer.valueOf(ini.getValueSafe(dName,"typeWorld","0"));;
					dsc.transmitEvery=1;
					dsc.serialNumber=Integer.valueOf(ini.getValueSafe(dName,"serialNumber","0"));
					s.addDevice(dsc);
				} else {
					DeviceModbusSlave dms = new DeviceModbusSlave(dName,this);
					dms.type=Device.getDeviceTypeByName(ini.getValueSafe(dName, "type", "DEVICE_TYPE_DISABLED"));
					dms.typeWorld=Integer.valueOf(ini.getValueSafe(dName,"typeWorld","0"));;
					dms.transmitEvery=1;
					dms.networkAddress=Integer.valueOf(ini.getValueSafe(dName,"networkAddress","0"));
					dms.startRegister=Integer.valueOf(ini.getValueSafe(dName,"startRegister","0"));
					dms.nRegisters=Integer.valueOf(ini.getValueSafe(dName,"nRegisters","0"));
					dms.serialNumber=Integer.valueOf(ini.getValueSafe(dName,"serialNumber","0"));;
					s.addDevice(dms);
				}
			}


			streams.add(s);			
		}

		for ( int i=0 ; i<streams.size() ; i++ ) {
			Stream s=streams.elementAt(i);
			if (stdErr) System.err.printf("# STREAM[%d] = %s\n",i,s);

			for ( int j=0 ; j<s.devices.size(); j++ ) {
				if (stdErr) System.err.printf("#\tdevice[%d] = %s\n", j, s.devices.elementAt(j));
			}

		}
	}


	public void openWorldDatas() {
		Iterator<WorldData> it = worlds.iterator();


		while (it.hasNext()) {
			WorldData w = it.next();

			if (stdErr) System.err.print("# Opening world data connection to " + w + " ...");
			if ( w.open() ) {
				if (stdErr) System.err.println(" sucessfully completed.");
			} else {
				if (stdErr) System.err.println(" failed. Adding to reconnect list");
				it.remove();
				worldsToReconnect.add(w);

			}
			if (stdErr) System.err.flush();
		}

	}

	public void closeWorldDatas() {
		Iterator<WorldData> it = worlds.iterator();

		while (it.hasNext()) {
			WorldData w = it.next();

			if (stdErr) System.err.print("# Closing world data connection to " + w + " ...");
			if ( w.close() ) {
				if (stdErr) System.err.println(" sucessfully completed.");
			} else {
				if (stdErr) System.err.println(" failed.");
			}
			if (stdErr) System.err.flush();
		}

	}

	public void openStreams() {
		Iterator<Stream> i = streams.iterator();

		while (i.hasNext()) {
			Stream s = i.next();

			if ( Stream.STREAM_TYPE_DISABLED==s.streamType )
				continue;

			if (stdErr) System.err.printf("# Opening " + s.description + " ...",i);
			if ( s.open() ) {
				if (stdErr) System.err.println(" sucessfully completed.");
			} else {
				if (stdErr) System.err.println(" failed.");
			}
			if (stdErr) System.err.flush();
		}

	}

	public void closeStreams() {
		Iterator<Stream> i = streams.iterator();

		while (i.hasNext()) {
			Stream s = i.next();

			if ( Stream.STREAM_TYPE_DISABLED==s.streamType )
				continue;

			System.err.printf("# Closing " + s.description + " ...");
			if ( s.close() ) {
				System.err.println(" sucessfully completed.");
			} else {
				System.err.println(" failed.");
			}
			System.err.flush();
		}

	}

	public void streamStartQuery() {
		if (stdOut) System.out.println("((((heartbeat))))");

		Iterator<Stream> it = streams.iterator();

		/* re-connect to anything we need to re-connect to */
		Iterator<WorldData> itConnect = worldsToReconnect.iterator();
		while (itConnect.hasNext()) {
			WorldData w = itConnect.next();

			if (stdErr) System.err.print("# Re-opening world data connection to " + w + " ...");
			if ( w.open() ) {
				if (stdErr) System.err.println(" sucessfully completed.");
				itConnect.remove();
				worlds.add(w);

			} else {
				if (stdErr) System.err.println(" failed.");
			}
			if (stdErr) System.err.flush();
		}


		/* start the query thread */
		while (it.hasNext()) {
			Stream s=it.next();
			//if (stdOut) System.out.println(s);
			//String format="# %s: streamId: %s, streamType: %s, serialPort: %s serialSpeed: %s with %s devices";

			if ( ! s.isOpen() )
				continue;

			new Thread(s).start();
		}
		measurementNumber++;
		memcacheWrite("STATE_MEASUREMENTNUMBER", Integer.toString(measurementNumber));

		if (stdOut) System.out.flush();
		if (stdErr) System.err.flush();
	}

	public void openProcesses() {
		Iterator<Process> i = processes.iterator();

		while (i.hasNext()) {
			Process p = i.next();

			System.err.printf("# Opening " + p.description + " ...",i);
			if ( p.open() ) {
				System.err.println(" sucessfully completed.");
			} else {
				System.err.println(" failed.");
			}
			System.err.flush();
		}

	}

	public void closeProcesses() {
		Iterator<Process> i = processes.iterator();

		while (i.hasNext()) {
			Process p = i.next();

			System.err.printf("# Closing " + p.description + " ...",i);
			if ( p.close() ) {
				System.err.println(" sucessfully completed.");
			} else {
				System.err.println(" failed.");
			}
			System.err.flush();
		}

	}



	public void start(String iniFileName) {
		measurementNumber=0;
		ini = new IniFile(iniFileName);



		if (stdOut) System.out.println("java.library.path: " + System.getProperty("java.library.path"));

		/* memcache */
		if ( false == ini.hasSubject("MEMCACHE") || ! ini.isTrue("MEMCACHE", "enabled") ) {
			System.err.println("# No memcache subject. Memcache interface disabled.");
			memcache=null;
		} else {
			String memcacheHost = ini.getValueSafe("MEMCACHE","host","localhost");
			int memcachePort = Integer.valueOf(ini.getValueSafe("MEMCACHE","port","11211"));

			try {
				memcache=new MemcachedClient(new InetSocketAddress(memcacheHost,memcachePort));
				/* clear memcache ... let's hope there isn't anything we care about on there */
				memcache.flush();
				
				ini.setMemcache(memcache,0);
			} catch ( IOException e ) {
				memcache=null;
			}
		}

		queryInterval=Integer.valueOf(ini.getValueSafe("GENERAL","interval","10000"));
		if ( queryInterval < 1000 )
			queryInterval=10000;

		serialPrefix=Integer.valueOf(ini.getValueSafe("GENERAL","serialPrefix","90"));
		serialNumber=Integer.valueOf(ini.getValueSafe("GENERAL","serialNumber","1111"));
		if (stdErr) System.err.println("# Serial Prefix: " + serialPrefix + " Serial Number: " + serialNumber);







		loadWorldDataConfig();
		loadProcessConfig(queryInterval);

		loadStreamsConfig();

		openWorldDatas();
		openProcesses();
		openStreams();




		/* start our master timer */
		heart = new javax.swing.Timer(queryInterval, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				streamStartQuery();
			}
		});

		/* should we start at the top of the second? */
		heart.start();

		/* Wait for a key press */
		try {
			System.in.read();
			if (stdErr) System.err.println("# shutting down in 2 seconds ...");

			heart.stop();
			Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		}

		heart.stop();

		closeProcesses();
		closeStreams();
		closeWorldDatas();
	}

	public static void main(String[] args) throws InterruptedException {
		if (stdErr) System.err.println("# PCTap device query master by APRS World, LLC");
		if (stdErr) System.err.println("# release from precision on 2014-02-26");


		String iniFilename="config_default.ini";

		if ( 1==args.length ) {
			iniFilename=args[0];
		}

		if (stdErr) System.err.println("# Loading configuration from: " + iniFilename);
		if (stdErr) System.err.println("# Press enter to terminate program.");
		PCTap w=new PCTap();
		w.start(iniFilename);


		if (stdErr) System.err.println("# Done");		
	}

}
