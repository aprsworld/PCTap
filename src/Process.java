import com.focus_sw.fieldtalk.MbusMasterFunctions;
import com.focus_sw.fieldtalk.MbusRtuMasterProtocol;
import com.focus_sw.fieldtalk.MbusSerialMasterProtocol;


public class Process implements Runnable {
	protected final boolean debug=false;

	protected IniFile ini;
	protected String iniSubject;
	protected String description;


	/* Watchdog Arlo Modbus master */
	protected MbusMasterFunctions mbus;
	protected int relayModbusAddress;

	protected double anemometerM;
	protected double anemometerB;
	protected int period;
	protected double lowwind;
	protected double midwind;
	protected double dbalimit;
	protected int wait;


	/* current conditions */
	protected double nowWindSpeedMS;
	protected double nowDBANear;
	protected double nowDBAFar;

	/* limited queues for doing time series averages */
	protected LimitedQueue<Double> qWindSpeedMS;
	protected LimitedQueue<Double> qDBANear;
	protected LimitedQueue<Double> qDBAFar;
	protected int qSize;

	protected long timeOfNext;

	public Process(int interval, String n, String s, IniFile i) {
		description=n;
		iniSubject=s;
		ini=i;

		/* if -1, we can process control logic OR if System.currentTimeMillis() is >= timeOfNext, we can process control logic */
		timeOfNext=-1L;

		nowWindSpeedMS=0.0;
		nowDBANear=0.0;
		nowDBAFar=0.0;



		anemometerM=Double.valueOf(ini.getValueSafe(iniSubject,"anemometerM","1"));
		anemometerB=Double.valueOf(ini.getValueSafe(iniSubject,"anemometerM","1"));

		period=Integer.valueOf(ini.getValueSafe(iniSubject,"period","60"));
		wait=Integer.valueOf(ini.getValueSafe(iniSubject,"wait","360"));

		lowwind=Double.valueOf(ini.getValueSafe(iniSubject,"lowwind","1"));
		midwind=Double.valueOf(ini.getValueSafe(iniSubject,"midwind","2"));
		dbalimit=Double.valueOf(ini.getValueSafe(iniSubject,"dbalimit","3"));

		
		/* setup up our FIFO queues for wind speed and sound readings */
		qSize = (period*1000) / interval; /* should be period / interval */
		qWindSpeedMS = new LimitedQueue<Double>(qSize);
		qDBANear = new LimitedQueue<Double>(qSize);
		qDBAFar = new LimitedQueue<Double>(qSize);


		System.err.printf("\n\n# %s created with interval=%d qsize=%d anemometer{m=%.4f b=%.4f} period=%d wait=%d lowwind=%.1f midwind=%.1f dbalimit=%.1f\n\n",
				iniSubject,
				interval,
				qSize,
				anemometerM,
				anemometerB,
				period,
				wait,
				lowwind,
				midwind,
				dbalimit
				);


	}

	public double qSum(LimitedQueue<Double> l) {
		double d=0.0;

		for ( int i=0 ; i<l.size() ; i++ ) {
			d += l.get(i);
		}
		return d;
	}


	public void addData(DataRSTap packet) {
		/* decode raw data from a packet */
		
		if ( 100 == packet.typeWorld ) {
			/* XRW2G for wind speed */
			if ( packet.qBuff[1] != 0 && packet.qBuff[1] != 65535 ) {
				nowWindSpeedMS = ((anemometerM*10000.0)/packet.qBuff[1] + anemometerB);
			} else {
				nowWindSpeedMS = 0.0;
			}

			System.err.printf("# Wind Speed (m/s): %.1f\n",nowWindSpeedMS);

			qWindSpeedMS.add(nowWindSpeedMS);
		} else if ( 4263217 == packet.deviceSerialNumber ) {
			/* Soundmeter near turbine */
			nowDBANear = ( ((packet.qBuff[5]&0xff)<<8) + (packet.qBuff[6]&0xff) )/10.0;

			System.err.printf("# Soundmeter near turbine reading: %.1f\n",nowDBANear);

			qDBANear.add(nowDBANear);
		} else if ( 4263218 == packet.deviceSerialNumber ) {
			/* Soundmeter furthest from turbine */
			nowDBAFar = ( ((packet.qBuff[5]&0xff)<<8) + (packet.qBuff[6]&0xff) )/10.0;

			System.err.printf("# Soundmeter furthest from turbine reading: %.1f\n",nowDBAFar);

			qDBAFar.add(nowDBAFar);
		} else { 
			return;
			
		}

		/* every time through we will check our limits ... not real efficient, but whatever */
		if ( qWindSpeedMS.size() < qSize || qDBANear.size() < qSize || qDBAFar.size() < qSize ) {
			System.err.printf("# Queues are not filled enough .... qWindSpeedMS.size()=%d qDBANear.size()=%d qDBAFar.size()=%d\n",
					qWindSpeedMS.size(),
					qWindSpeedMS.size(),
					qWindSpeedMS.size()
					);

			return;
		} 

		double qSumWS, qSumNear, qSumFar;
		qSumWS=qSum(qWindSpeedMS);
		qSumNear=qSum(qDBANear);
		qSumFar=qSum(qDBAFar);

		double avgWS, avgNear, avgFar;
		avgWS = qSumWS / qSize;
		avgNear = qSumNear / qSize;
		avgFar = qSumFar / qSize;
		System.err.printf("# average {ws=%.1f dBa near=%.1f dBa far=%.1f}\n",avgWS, avgNear, avgFar);


		/* make sure we aren't locked out */
		if ( -1 == timeOfNext || timeOfNext <= System.currentTimeMillis() ) {
			timeOfNext=-1L;
			
			if ( avgWS < lowwind ) {
				relayOff();

				return;
			}

			/* if (dba1average-dba2average > dbalimit) and (windaverage>midwind) then [shutdown] */
			if ( (avgNear-avgFar) > dbalimit && avgWS > midwind ) {
				relayOn();
				
				/* we don't get to process control logic until wait seconds into the future */
				timeOfNext = System.currentTimeMillis() + (wait*1000);

				return;
			} else {
				relayOff();
			}
		} else {
			System.err.printf("# In waiting period (%d seconds left) ... relay is ON\n",(int) ((timeOfNext - System.currentTimeMillis()) / 1000) );
			
		}




	}

	public void run() {
		System.err.println("# Process.run() was called ... what now?");
	}

	public String toString() {
		return description;
	}

	public boolean relayOff() {
		System.err.println("#### Relay Off ###");
		try {
			mbus.writeSingleRegister(relayModbusAddress, 1, (short) 0);
		} catch ( Exception e ) {
			System.err.println("# Error in process relayOff(): " + e);
			return false;
		}
		return true;
	}

	public boolean relayOn() {
		System.err.println("#### Relay On ###");
		try {
			mbus.writeSingleRegister(relayModbusAddress, 1, (short) 1);
		} catch ( Exception e ) {
			System.err.println("# Error in process relayOn(): " + e);
			return false;
		}
		return true;	}

	public boolean open() {
		/* open our Modbus connection to the Watchdog Arlo */
		mbus = new MbusRtuMasterProtocol();

		String serialPort=ini.getValueSafe(iniSubject, "relaySerialPort", "");
		int serialSpeed=Integer.valueOf(ini.getValueSafe(iniSubject,"relaySerialSpeed","0"));
		relayModbusAddress=Integer.valueOf(ini.getValueSafe(iniSubject,"relayModbusAddress","1"));


		try {
			mbus.setTimeout(500);
			mbus.setRetryCnt(2); // Increase to 2 for poor links
			mbus.setPollDelay(0); // Increase if slave needs time between polls
			((MbusRtuMasterProtocol) mbus).openProtocol(serialPort, serialSpeed,	MbusSerialMasterProtocol.DATABITS_8, MbusSerialMasterProtocol.STOPBITS_2, MbusSerialMasterProtocol.PARITY_NONE);
		} catch ( Exception e ) {
			System.err.println("# Caught exception while opening Modbus Serial for " + iniSubject + " (" + serialPort + " @ " + serialSpeed + ")");
			System.err.println(e);
		}


		return mbus.isOpen();
	}


	public boolean close() {
		try {
			mbus.closeProtocol();
		} catch ( Exception e ) {
			System.err.println("# Error closing Modbus port for " + iniSubject);
			System.err.println(e);
		}

		return ! mbus.isOpen();
	}

}
