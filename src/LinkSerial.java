import java.io.*;
import java.util.*;
//import gnu.io.*;
import javax.comm.*;

public class LinkSerial {
	protected SerialPort p = null;
	protected BufferedReader i = null;
	protected InputStream is = null;
	protected PrintWriter o = null;
	protected OutputStream os = null;

	protected String wantedPortName;
	protected int serialSpeed;

	public LinkSerial(String serialPort, int speed) {
		wantedPortName=serialPort;
		serialSpeed=speed;
	}
	
		
	public boolean Connect() {
		/* Get all available ports */
		Enumeration<?> portIdentifiers = CommPortIdentifier.getPortIdentifiers();

		/* Find requested port */
		CommPortIdentifier portId = null;
		while (portIdentifiers.hasMoreElements()) {
			CommPortIdentifier p = (CommPortIdentifier)portIdentifiers.nextElement();
			if (p.getPortType() == CommPortIdentifier.PORT_SERIAL && p.getName().equals(wantedPortName)) {
				portId = p;
				break;
			}
		}

		/* Found port? */
		if (portId == null) {
			System.err.println("# Failed to find serial port.");
			return false;
		}

		/* Open port */
		try {
			p = (SerialPort)portId.open("name", 100);
		} catch (PortInUseException e) {
			System.err.println("# Serial port already in use." + e);
			return false;
		}

		/* Setup port */
		try {
			p.setSerialPortParams(serialSpeed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			p.enableReceiveTimeout(65000); /* as long of timeout as possible, fight win32 rxtx bug */
		} catch ( Exception e ) {
			System.err.println("# Error configuring serial port.");
			return false;
		}

		/* Create streams for port */
		try {
			i = new BufferedReader(new InputStreamReader(p.getInputStream()));
			is = p.getInputStream();
			o = new PrintWriter(p.getOutputStream(), true);
			os = p.getOutputStream();
		} catch (IOException e) {
			System.err.println("# Failed to create stream.");
			return false;
		}

		p.disableReceiveThreshold();

		return true;
	}
	
	public void sendLine(String s) {
		try {
			o.print(s);
			o.flush();
		} catch ( Exception e ) {
			System.err.println("# sendLine had an exception: " + e);
		}
	}

	public void sendLine(byte buff[]) {

		try {
			os.write(buff);
			os.flush();
		} catch ( Exception e ) {
			System.err.println("# sendLine had an exception: " + e);
		}
	}

	
	public boolean dataReady ( ) {
		try {
			return i.ready();
		} catch ( Exception E ) {
			return false;
		}
	}
	
	public String getLine() {
		String line;

      while ( true ) {
			try {
				line=i.readLine();
				
				/* reached the end of the stream */
				if ( line == null ) {
					break;
				}
			} catch ( IOException e ) {
				System.err.println("# readLine had an exception. Probably due to windows rxtx bug: " + e);
				continue;
			}
			
			if ( line != null ) {
				return line;
			}
		}

		return "";

/*
		try {
			line = i.readLine();
		} catch (IOException e) {
			line = null;
		}
		return line;
*/
	}
	
	public boolean Disconnect() {
		try {
			o.close();
			i.close();
			p.close();
		} catch (IOException e) {
			System.err.println("Failed to close.");
			return false;
		}

		return true;
	}


    public void emptyInputBuffer() {
      try {
         while ( i.ready() ) {
            i.read();
            //System.err.print(".");
         }
      } catch (IOException e) {
         e.printStackTrace();
      }

    }


}
