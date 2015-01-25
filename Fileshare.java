import java.net.*;
import java.io.*;
import java.util.*;
import java.math.*;

public class Fileshare {
	public static void main(String[] args) {
		String result;
		boolean done = false;
		Scanner in = new Scanner(System.in);

		while (done == false) {
			System.out.print("Do you wish to send or receive files (S/R)? ");
			result = in.nextLine();
			System.out.println();

			if (result.equalsIgnoreCase("S")) {
				Server server = new Server();
				done = server.StartServer();

			} else if (result.equalsIgnoreCase("R")) {
				Client client = new Client();
				done = client.StartClient();

			} else {
				System.out.println("Invalid answer, try again!");
			}

		}

	}
}

class Server {
	Scanner in = new Scanner(System.in);
	ServerSocket serverSocket;
	ArrayList<Socket> clients = new ArrayList<Socket>();
	File[] files;
	int fileNr = 0;

	boolean StartServer() {
		try {
			serverSocket = new ServerSocket(9889);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		ServerMenu();
		GetFiles();
		SendFiles();
		return true;
	}

	void ServerMenu() {
		String hostName = "ERROR";
		String ip = "ERROR";
		try {
			hostName = InetAddress.getLocalHost().getHostName();

			URL whatismyip = new URL("http://checkip.amazonaws.com");
			BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));

			ip = in.readLine();
		} catch (UnknownHostException e) {
			System.out.println("Couldn't find host name");
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("Couldn't find IP");
			e.printStackTrace();
		}
		System.out.println("If on the same network: " + hostName);
		System.out.println("If connecting remote: " + ip);
		System.out.println();
		System.out.print("How many clients are you expecting? ");
		int nr = in.nextInt();
		System.out.println();
		System.out.println("Waiting for clients to connect.");

		for (int i = clients.size(); i < nr; i++) {
			Socket connection = new Socket();
			try {
				connection = serverSocket.accept();
			} catch (IOException e) {
				System.out.println("Connection failed!");
				e.printStackTrace();
			}
			clients.add(connection);
			System.out.println(connection.getPort() + " connected! (" + (i+1) + "/" + nr + ")");
		}
	}

	void GetFiles() {
		boolean done = false;
		File folder = new File(System.getProperty("user.dir"));
		files = folder.listFiles();

		System.out.println("\nCurrently selected files: ");

		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile()) {
				System.out.println(files[i].getName());
				fileNr++;
			}
		}
		System.out.println();
	}

	void SendFiles() {
		try {
			for (int i = 0; i < clients.size(); i++) {
				NotifyQue();
				System.out.println("Preparing file transfer to " + clients.get(i).getPort());
				OutputStream os = clients.get(i).getOutputStream();
				DataOutputStream ds = new DataOutputStream(os);
				ds.writeInt(fileNr);
				DataInputStream dis = new DataInputStream(clients.get(i).getInputStream());

				for (int j = 0; j < files.length; j++) {
					if (files[j].isFile()) {
						ds.writeChars(files[j].getName() + "|");
						ds.writeInt((int)files[j].length());

						byte[] bytearray = new byte[(int) files[j].length()];
						BufferedInputStream bin = new BufferedInputStream(new FileInputStream(files[j]));
						bin.read(bytearray, 0, bytearray.length);
						System.out.println(bytearray.length);

						System.out.println("Sending " + files[j].getName() + " to " + clients.get(i).getPort());
						int currentTot = 0;
						if (bytearray.length > 64000) {
							int rounds = bytearray.length/64000;
							for (int k = 0; k < rounds; k++) {
								os.write(bytearray, currentTot, 64000);
								currentTot += 64000;
								os.flush();
								if (dis.readInt() != 1) {
									System.exit(1);
								}
							}
						}
						os.write(bytearray, currentTot, bytearray.length-currentTot);
						os.flush();
						if (dis.readInt() != 1) {
							System.exit(1);
						}
					}
				}
				System.out.println("File transfer to " + clients.get(i).getPort() + " complete!\n");
				clients.get(i).close();
			}
			System.out.println("File transfer complete");
		} catch (IOException e) {
			System.out.println("Error occured while sending files!");
			e.printStackTrace();
		}
	}

	void NotifyQue() {
		try {
			for (int i = 0; i < clients.size(); i++) {
				OutputStream os = clients.get(i).getOutputStream();
				DataOutputStream ds = new DataOutputStream(os);
				ds.writeInt(i);
			}
		} catch (IOException e) {
			System.out.println("Error occured while updating the que!");
			e.printStackTrace();
		}
	}
}

class Client {
	Scanner in = new Scanner(System.in);
	boolean done = false;
	String host;
	Socket socket;
	InputStream is;
	DataInputStream ds;
	DataOutputStream dos;

	int numberFiles;
	String fileName = "";
	int filesize;
	int bytesRead;
	int currentTot = 0;

	boolean StartClient() {
		GetHost();

		try {
			is = socket.getInputStream();
			ds = new DataInputStream(is);
			dos = new DataOutputStream(socket.getOutputStream());

			Que();

			numberFiles = ds.readInt();
			System.out.println("Downloading " + numberFiles + " files.\n");

			for (int i = 0;  i < numberFiles; i++) {
				char c;

				do {
					c = ds.readChar();
					if (c != '|') {
						fileName = fileName + c;

					}
				} while (c != '|');

				filesize = ds.readInt();
				System.out.print("Downloading: " + fileName + " Filesize: ");
				double size = filesize;
				String id = " bytes.";

				if (filesize > 1000 && filesize < 1000000) {
					size = (double)filesize/1000.0;
					id = " KB.";
				} else if (filesize > 1000000 && filesize < 1000000000) {
					size = (double)filesize/1000000.0;
					id = " MB.";
				} else if (filesize < 1000000000) {
					size = (double)filesize/1000000000.0;
					id = " GB.";
				}
				size = round(size, 2);

				System.out.println(size + id);

				byte[] bytearray  = new byte[filesize];
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(fileName)));
				bytesRead = is.read(bytearray, 0, bytearray.length);
				currentTot = bytesRead;
				dos.writeInt(1);
				dos.flush();

				System.out.println(bytesRead);
				while(currentTot < filesize) {
					bytesRead =
					is.read(bytearray, currentTot, (bytearray.length-currentTot));
					if(bytesRead >= 0) currentTot += bytesRead;
					dos.writeInt(1);
					dos.flush();
				}
				bos.write(bytearray, 0 , currentTot);
				bos.flush();
				bos.close();
				fileName = "";
			}
			socket.close();
			System.out.println("\nFiles received successfully!");
		} catch (IOException e) {
			System.out.println("Error occured while downloading files!");
			e.printStackTrace();
		}
		return true;
	}

	void GetHost() {
		try {
			System.out.print("Please enter the host name or IP adress you want to connect to (to exit loop, enter: cancel): ");
			host = in.nextLine();
			if (host.equalsIgnoreCase("Cancel")) {
				System.out.println("Connection canceled!");
				System.exit(0);
			}
			System.out.println();
			
			socket = new Socket(host,9889);
		} catch (UnknownHostException uhe) {
			System.out.println("The host you entered could not be found, make sure it was spelled correctly.");
			GetHost();

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Connected!\n");
	}

	void Que() {
		int number = Integer.MAX_VALUE;
		do {
			try {
				number = ds.readInt();
			} catch (IOException e) {
				System.out.println("Error occured while receiveing que number!");
				e.printStackTrace();
			}
			System.out.println("You are currently number " + number + " in the line.");
		} while (number != 0);
		System.out.println();
	}

	double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}
}
