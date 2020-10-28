package logica;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class UDPServer {
	
	public final static String SYN="SYN";
	
	public final static String SYNACK="SYNACK";
	
	public final static String ACK="ACK";
	
	public final static String DATA="DATA";
	
	public final static String REJECT="REJECT";
	
	public final static int MAX_NUMBER_CONNECTIONS = 25;
	
	public final static String[] PATHS = {"../data/pruebaTexto.txt", "../data/100MiBFile.mp4","../data/256MiBFile.dat", "../data/texto.txt"};
	
	private int port;
	
	private DatagramSocket serverSocket;
	
	private InetAddress[] clientsIP;
	
	private int[] clientsPort;
	
	private int clientNumber;
	
	private int fileNum;
	
	/**
	 * Metodo que crea la clase del servidor udp.
	 * @param port
	 * @param clientNumber
	 * @param fileNum
	 * @throws Exception
	 */
	public UDPServer(int port, int clientNumber, int fileNum) throws Exception{
		this.port = port;
		this.fileNum = fileNum;
		this.serverSocket = new DatagramSocket(this.port);
		this.clientNumber = (clientNumber>MAX_NUMBER_CONNECTIONS)? MAX_NUMBER_CONNECTIONS: clientNumber;
		this.clientsIP = new InetAddress[this.clientNumber];
		this.clientsPort = new int[this.clientNumber];
	}
	
	/**
	 * Metodo que inicia la espera por los clientes del servidor, una vez se conectan los clientes necesarios
	 * el servidor esta a la espera de iniciar la transmision del archivo
	 * @throws Exception
	 */
	public void acceptConnections() throws Exception{
		int connectionsEstablished = 0;
		byte[] receiveData = new byte[1024];
		byte[] sendData = new byte[1024];
		// Se espera a que se acumulen igual numero de clientes que conexiones que debe tener el servidor
		while (connectionsEstablished!=clientNumber) {
			DatagramPacket receivePacket =
	             new DatagramPacket(receiveData, receiveData.length);
			serverSocket.receive(receivePacket);
			String sentence = new String(receivePacket.getData());
			sentence = sentence.replaceAll("\0", "");
			// Se corrobora que mensaje enviado a servidor posea la palabra SYN
			// En caso de tenerla se identifica como cliente del servidor
			if(sentence.equals(SYN)) {
				System.out.println("Nueva conexions, numero: "+ connectionsEstablished+1);
				InetAddress IPAddress = receivePacket.getAddress();
				// Se guarda la IP del cliente
				clientsIP[connectionsEstablished] = IPAddress;
				System.out.println("IP Cliente: " + IPAddress);
				// Se guarda el puerto asociado al cliente
				int portCliente = receivePacket.getPort();
				clientsPort[connectionsEstablished] = portCliente;
				System.out.println("Puerto Cliente: " + portCliente);
				sendData = SYNACK.getBytes();
				DatagramPacket sendPacket =
			             new DatagramPacket(sendData, sendData.length, IPAddress, portCliente);
				// Se notifica a cliente que hara parte de la transmision y se adiciona a contador
				// de clientes
				serverSocket.send(sendPacket);
				connectionsEstablished ++;
			}
				
		}
	}
	
	/**
	 * Metodo que inicia la transmision del archivo elegido, debido a que UDP no orientado a conexion no es necesario un socket dedicado a cada cliente
	 * @throws Exception
	 */
	public void startTransmission() throws Exception{
		int size_buffer = 512;
		byte[] receiveData = new byte[size_buffer];
		byte[] sendData = new byte[size_buffer];
		File file = new File(PATHS[fileNum]);
		String checksumString = getFileChecksum(file);
		int numberPackets = (int) Math.ceil((double) file.length()/(double) size_buffer);
		// Primero de prepara un mensaje con la informacion de la transmision 
		String data = DATA+" "+checksumString+" "+numberPackets+" "+file.getName();
		println(data);
		// Secuenciamente se toma cada cliente y realiza el procedimiento sobre cada uno
		boolean skip = false;
		for(int i = 0; i < this.clientNumber; i++) {
			println("Se inicio envio con cliente: "+i);
			// Se define un timeout de 3 segundos para el servidor, esto con el fin de que si alguno de los clientes no constesta algun mensaje de confirma-
			// cion el servidor no se quede esperando por el
			serverSocket.setSoTimeout(3000);
			long start = System.nanoTime();
			// Se extrae la ip y el puerto del cliente i 
			InetAddress ipCliente = clientsIP[i];
			int puertoCliente = clientsPort[i];
			sendData = data.getBytes();
			DatagramPacket sendPacket =
		             new DatagramPacket(sendData, sendData.length, ipCliente, puertoCliente);
			// Se envia informacion de la transmision al cliente 
			serverSocket.send(sendPacket);
			println("Se enviaron datos de transmision");
			DatagramPacket receivePacket =
		             new DatagramPacket(receiveData, receiveData.length);
			// Se espera recibir confirmacion de cliente de que esta preparado para recibir el archivo
			String resp = "";
			try {
				serverSocket.receive(receivePacket);
				
				resp = new String(receivePacket.getData());
				resp = resp.replaceAll("\0", "");
				// Si la respuesta enviada no tiene el formato esperado tambien se salta al cliente
				if(!resp.equals(ACK))
					skip = true;
			} catch (SocketTimeoutException e) {
				// En caso de que el cliente nunca notifique que esta preparado para recibir el archivo
				// se salta al cliente y se continua con el resto
				skip = true;
			}
			if(!skip) {
				// Si no se desea salta al clinete se entre a esta porcion de codigo
				println("Se recibio confirmacion de envio de datos");
				// Por cada cliente se inicia una lectura del archivo a enviar
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis); 
				sendData = new byte[size_buffer];
				long current = 0;
				println("Se inicia envio de archivos");
				// Se inicia transmision del archivo hasta que se envien todos los paquetes
				while(current != numberPackets) {
					sendData = new byte[size_buffer];
					bis.read(sendData);
					sendPacket = new DatagramPacket(sendData, sendData.length, ipCliente, puertoCliente);
					serverSocket.send(sendPacket);
					current++;
				}
				// Una vez se termina de enviar el archivo se espera a que el cliente envie hash calculado por el. Se aumenta el timeout en casod e que el cliente
				// deba esperar a su timeout porque no todos los paquetes llegaron
				println("Numero de paquetes enviados: "+numberPackets);
				serverSocket.setSoTimeout(6000);
				try {
					serverSocket.receive(receivePacket);
					// Se extrae el hash enviado por el cliente
					String sentence = new String(receivePacket.getData());
					sentence = sentence.replaceAll("\0", "");
					println("Se recibio hash de confirmacion");
					// Se toma tiempo y se escibe en log
					long end = System.nanoTime();
			    	double seconds = (end-start)/(double)1000000000;
					log(file.getName(), sentence.equals(checksumString), seconds, ipCliente);
					bis.close();
				} catch (SocketTimeoutException e) {
					// En caso de que no se reciba notificaion de hash del cliente no se genera log y se pasa al siguiente cliente
					println("No se pudo comprobar transmision ya que cliente nunca mando hash");
				}
				
			}
			else {
				println("Se salto usuario por no dar respuesta a informacion envio");
				skip = false;
			}
		}
	}
	
	
	
	private String getFileChecksum(File file) throws IOException, NoSuchAlgorithmException{
		MessageDigest digest = MessageDigest.getInstance("SHA-1");
	    FileInputStream fis = new FileInputStream(file);
	    byte[] byteArray = new byte[1024];
	    int bytesCount = 0; 
	    while ((bytesCount = fis.read(byteArray)) != -1) {
	        digest.update(byteArray, 0, bytesCount);
	    };
	    fis.close();
	    byte[] bytes = digest.digest();
	    StringBuilder sb = new StringBuilder();
	    for(int i=0; i< bytes.length ;i++){
	        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
	   return sb.toString();
	}
	
	
	public static void log(String nombreArchivo, boolean estado, double seconds, InetAddress ip) throws Exception {
		File file = new File("../data/FileNumber.txt");
		if(!file.exists()) {
			file.createNewFile();
		}
		BufferedReader br = new BufferedReader(new FileReader(file)); 
		int n = 0;
		String line = br.readLine();
		if(line!=null) {
			n = Integer.parseInt(line)+1;
		}
		PrintWriter writer = new PrintWriter(file);
		writer.print(""+n);
		writer.close();
		String logPath = "../data/log"+n+".txt";
		file = new File(logPath);
		if(!file.exists()) {
			file.createNewFile();
		}
		writer = new PrintWriter(file);
		Date date = Calendar.getInstance().getTime();  
        DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");  
        file = new File("../data/"+nombreArchivo);
		writer.println("");
		writer.println("Cliente: "+ip);
		writer.println("Nombre archivo enviado: "+file.getName());
		writer.println("Tamaño archivo: "+file.length()+" Bytes");
		writer.println("Fecha: "+ dateFormat.format(date));
		writer.println("Tiempo: "+ seconds+" seg");
		writer.println("Envio "+ estado);
		writer.println("");
		writer.close();
		br.close();
	}
	
	
	private void println(String string) {
		System.out.println(string);
	}
	
	
	
	
	
}
