package logica;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;

public class UDPClient {
	
	public final static String SYN="SYN";
	
	public final static String SYNACK="SYNACK";
	
	public final static String ACK="ACK";
	
	public final static String DATA="DATA";

	private static String ipServer = "192.168.1.11";
	
	public static void main(String[] args)throws Exception{
		// Se inicializa la aplicacion y se espera a que el usuario ingrese el puerto del servidor
		Scanner linea = new Scanner(System.in);
		System.out.println("Ingrese el puerto del servidor");
		int port = Integer.parseInt(linea.next());
        DatagramSocket socket = new DatagramSocket();
        // Se define un tiempo de timeout de 3 segundos, esto en caso de que el servidor 
        // no responde a la solicitud inicial para participar en la transmision
        socket.setSoTimeout(3000);
        byte[] sendData = new byte[1024];
        byte[] receiveData = new byte[1024];
        sendData = SYN.getBytes();
        InetAddress inetAdress = InetAddress.getByName(ipServer);
        DatagramPacket sendPacket =
                new DatagramPacket(SYN.getBytes(), SYN.getBytes().length, inetAdress, port);
        // Se notifica al servidor de la participacion en la transmision
        socket.send(sendPacket);
        DatagramPacket receivePacket =
                new DatagramPacket(receiveData, receiveData.length);
        // Se espera a que el cliente repsonda la confirmacion de la participacion
        socket.receive(receivePacket);
        String resp = new String(receivePacket.getData());
        resp = resp.replaceAll("\0", "");
        if(resp.equals(SYNACK)) {
        	System.out.println("Confirmacion recibida");
        }
        else {
        	linea.close();
        	socket.close();
        	throw new Exception();
        }
        System.out.println("Esperando recepcion de informacion archivo");
        // Se espera a que inicie la recepcion de datos, debido a que para este puto el sistemas 
        // el servidor puede tadarse en iniciar el envio debido a que debe esperar a que se acumulen 
        // todos los clientes necesarios se puso un tiempo de 250 segundos de timeout
        socket.setSoTimeout(250000);
        socket.receive(receivePacket);
        // Una vez se recibe la informacionacerca del envio se corrobora y en caso de ser
        // correcta se notiifca al servidor
        resp = new String(receivePacket.getData());
        resp = resp.replaceAll("\0", "");
        long start = System.nanoTime();
        // Se corrobora informacion
        if(resp.startsWith(DATA)) {
        	System.out.println("Metadatos reibidos");
        }
        else {
        	linea.close();
        	socket.close();
        	throw new Exception();
        }
        
        
        String[] data = resp.split(" ");
        String fileHash = data[1];
        long numberPackets = Long.parseLong(data[2]);
        String fileName = data[3];
        FileOutputStream fos = new FileOutputStream("./data/"+fileName);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        System.out.println(resp);
        long packetsReceived = 0;
        boolean received = false;
        // Se establecio un buffer de 512 bytes
        int size_buffer = 512;
        receiveData = new byte[size_buffer];
        receivePacket = new DatagramPacket(receiveData, size_buffer);
        // Se redice el tiempo de timeout durante el envio del archivo esto para que no se quede esperando 
        // mucho tiempo a que paquetes seguramente perdidos llegue
        socket.setSoTimeout(3000);
        // Se espera a que lleguen todos los paquetes de la trnasmision, en caso de que no se 
        // finaliza la recepcion y se ausme que todos los pquetes no llegaron
        sendData = ACK.getBytes();
        sendPacket = new DatagramPacket(sendData, sendData.length, inetAdress, port);
        // Se envia notificacion de informacion de transmision recibida justo antes de empezar recepcion archivo 
        socket.send(sendPacket);
        while(!received) {
        	try {
        		receiveData = new byte[size_buffer];
				socket.receive(receivePacket);
				packetsReceived+= 1;
				bos.write(trim(receivePacket.getData()));
				if(packetsReceived==numberPackets) {
					received = true;
					System.out.println("Llegaron todos los paquetes");
				}
			} catch (SocketTimeoutException e) {
				System.out.println("Se tuvo que detener transmision no todos los paquetes llegaron");
				received = true;
			}
        }
        
        bos.close();
        // Se calulca el hash para el archivo recibido y se envia al servidor
        System.out.println("Numero de paquetes recibidos: "+packetsReceived);
        String hashCalculadoString = getFileChecksum(new File("./data/"+fileName));
        System.out.println(hashCalculadoString);
        sendData = hashCalculadoString.getBytes();
        sendPacket = new DatagramPacket(sendData, sendData.length, inetAdress, port);
        socket.send(sendPacket);
        long end = System.nanoTime();
    	double seconds = (end-start)/(double)1000000000;
    	// Se genera el log de la transmision
    	
        log(fileName, hashCalculadoString.equals(fileHash), seconds);
        socket.close();
        linea.close();
        
    }
	
	private static String getFileChecksum(File file) throws IOException, NoSuchAlgorithmException
	{
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
	    for(int i=0; i< bytes.length ;i++)
	    {
	        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
	   return sb.toString();
	}
	
	
	public static void log(String nombreArchivo, boolean estado, double seconds) throws Exception {
		File file = new File("./data/FileNumber.txt");
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
		String logPath = "./data/log"+n+".txt";
		file = new File(logPath);
		if(!file.exists()) {
			file.createNewFile();
		}
		writer = new PrintWriter(file);
		Date date = Calendar.getInstance().getTime();  
        DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");  
        file = new File("./data/"+nombreArchivo);
		writer.println("");
		writer.println("Nombre archivo enviado: "+ file.getName());
		writer.println("Tamaño archivo: "+file.length()+" Bytes");
		writer.println("Fecha: "+ dateFormat.format(date));
		writer.println("Tiempo: "+ seconds+" seg");
		writer.println("Envio "+ estado);
		writer.println("");
		br.close();
		writer.close();
	}
	
	static byte[] trim(byte[] bytes){
	    int i = bytes.length - 1;
	    while (i >= 0 && bytes[i] == 0){
	        --i;
	    }
	    return Arrays.copyOf(bytes, i + 1);
	}
}
