package indi.isnow.japns.service;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IApnsService {
	private final static Logger logger = LoggerFactory.getLogger(IApnsService.class);
	
	
	SSLContext sslContext;
	Socket socket;
	boolean monitSocket;
	
	public Socket getOrCreateSocket() throws IOException{
		socket = sslContext.getSocketFactory().createSocket();
		socket.setKeepAlive(true);
		socket.setSoTimeout(1000);
		
		if (monitSocket) {
			
		}
		
		return socket;
	}
	
	public void sendMessage(){
		
	}
	
	public void monitSocket(final Socket socket){
		
		Runnable monitor = new Runnable(){
			private final static int PACKET_SIZE = 6;
			public void run() {
				InputStream in = null;
				try {
					in = socket.getInputStream();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				try {
					byte[] packet = new byte[PACKET_SIZE];
					while (readPacket(in, packet)) {
						try {
							socket.close();
						}catch (IOException e) {
							logger.error("Close socket error when send message failed.", e);
						}
					}
				} catch (IOException e) {
					logger.error("Reading socket error.", e);
				}
			}
			
			private boolean readPacket(InputStream in, byte[] packet) throws IOException{
				if (in == null)
					return false;
				
				int n = 0;
				while (n < PACKET_SIZE){
					try {
						int count = in.read(packet, n, PACKET_SIZE);
						if (count < 0)
							throw new EOFException("EOF error after reading " + n + " bytes");
						
						n = n + count;
					} catch (IOException e) {
						if (n == 0)
							return false;
						throw new IOException("Error after reading" + n + " bytes of packet", e);
					}
				}
				return true;
			}
			
			/*public ErrorResponsePacket getErrorResponsePacket(byte[] packet){
				List bytes = Arrays.asList(packet);
				int command = 0;
				int status = 0;
				int identifier = 0;
				try {
					command = Integer.parseInt(new String(new byte[] {(Byte) bytes.get(0)}, "utf-8"));
					status = Integer.parseInt(new String(new byte[] {packet[1]}, "utf-8"));
					identifier = Integer.parseInt(new String(Arrays.copyOfRange(packet, 2, 6), "utf-8"));
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				return new ErrorResponsePacket(command, status, identifier);
			}*/
		};
	}
}
