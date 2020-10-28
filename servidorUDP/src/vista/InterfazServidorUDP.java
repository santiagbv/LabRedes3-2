package vista;

import java.util.Scanner;

import logica.UDPServer;

public class InterfazServidorUDP {
	
	public static UDPServer udpServer;
	public static void main(String[] args) throws Exception
	{
		Scanner linea = new Scanner(System.in);
		boolean fin = false; 
		while(!fin){
			printMenu();
			int option = linea.nextInt();
			switch(option){
			case 1: 
				System.out.println("Ingrese el puerto del servidor");
				int puerto = Integer.parseInt(linea.next());
				System.out.println("Ingrese numero de conexiones");
				int conexiones = Integer.parseInt(linea.next());
				System.out.println("Ingrese el archivo que desea enviar");
				int intArchivo = Integer.parseInt(linea.next());
				udpServer= new UDPServer(puerto, conexiones, intArchivo);
				System.out.println("Se empezaran a recibir conexiones");
				udpServer.acceptConnections();
				System.out.println("Se finalizo recepcion conexiones");
				String resp = "";
				System.out.println("Desea iniciar transmision? Y");
				while(!resp.equals("Y")) {
					resp = linea.next();
				}
				udpServer.startTransmission();
				fin = true;
				break;
			case 2: 
				fin = true;
				linea.close();
				break;
			default: 
				break;
			}
		}
	}
	private static void printMenu(){
		System.out.println("1: Iniciar Transmision");
		System.out.println("2: Salir");
	}

}
