import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class WorldData {
	public String host;
	public int port;
	protected Socket sock;
	protected InputStream inputStream;
	protected OutputStream outputStream;
	protected final boolean debug=false;


	public boolean sendPacket(byte buff[]) {
//		if ( null==sock || ! sock.isConnected() || ! sock.isOutputShutdown() )
//			open();
		
		try {
			outputStream.write(buff, 0, buff.length);
			outputStream.flush();
		} catch ( Exception e) {
			System.err.println("# error sending packet to WorldData");
			System.err.println("# " + e.toString());
			if ( debug )
				e.printStackTrace();
			close();
			return false;
		}

		if ( debug ) {
			System.err.println("# dumping packet we just sent to WorldData");
			for ( int i=0 ; i<buff.length ; i++ ) {
				System.err.printf("# WorldData[%d] 0x%02x\n",i,buff[i]);
			}
		}

		return true;
	}


	public WorldData(String h, int p) {
		host=h;
		port=p;
	}

	public String toString() {
		return "host=" + host + ":" + port;
	}

	public boolean open() {
		try {
			sock=new Socket(host,port);
			inputStream=sock.getInputStream();
			outputStream=sock.getOutputStream();
		} catch ( UnknownHostException e ) {
			System.err.println(e);
			return false;
		} catch (IOException e) {
			System.err.println(e);
			return false;
		}

		return sock.isConnected();
	}

	public boolean close() {
		if ( sock.isClosed() )
			return true;
		
		try {
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return sock.isClosed();
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public boolean isConnected() {
		return sock.isConnected();
	}
	
	public String getConnectionState() {
		if ( null == sock ) 
			return "[null]";

		String s="";
				
		if ( sock.isBound() )
			s += "[isBound] ";
		if ( sock.isClosed() )
			s += "[isClosed] ";
		if ( sock.isConnected() )
			s += "[isConnected] ";
		if ( sock.isInputShutdown() )
			s += "[isInputShutdown] ";
		if ( sock.isOutputShutdown() )
			s += "[isOutputShutdown] ";		
		
		
		return s;
	}
}
