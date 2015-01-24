import java.net.*;
import java.io.*;
import java.util.*;
import java.math.*;
import java.text.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;

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
	Encryption en = new Encryption();
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

		InitEncrypt(en);
		ServerMenu();
		GetFiles();
		SendFiles();
		return true;
	}

	void ServerMenu() {
		String hostName = "ERROR";
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			System.out.println("Couldn't find host name");
			e.printStackTrace();
		}
		System.out.println("If on the same network: " + hostName);
		System.out.println("If connecting remote: ");
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

	void InitEncrypt(Encryption en) {
		try {
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			en.aesCipher = Cipher.getInstance("AES");
			en.aesKey = keyGen.generateKey();
		} catch (Exception e) {
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

class Encryption {
	Cipher aesCipher;
	SecretKey aesKey;

	String EncryptKey(String input) {
		DateFormat df = new SimpleDateFormat("dd;MM;yy;HH;mm");
		Calendar calobj = Calendar.getInstance();

		String sKey = (String) aesKey.getEncoded();
		char[] key = sKey.toCharArray();
		int[] iKey = new int[key.length];
		for (int i = 0; i < iKey.length; i++) {
			iKey[i] = (int) key[i];
		}

		for (int i = iKey.length-1; i < -1; i--) {
			if (i != 0) {
				key[i] = (char) ((((iKey[i]*iKey[i-1]) + 4)-74)*2);

			} else {
				String solution = df.format(calobj.getTime());
				char[] cSolution = solution.toCharArray();
				int[] iSolution = new int[cSolution.length];

				for (int j = 0; j < iSolution.length; j++) {
					iSolution[j] = (int) cSolution[j];
				}

				int value = iSolution[0] + iSolution[1] - iSolution[2] + iSolution[3] - iSolution[4];

				key[0] = (char) ((((iKey[0]*value) + 4)-74)*2);
			}
		}
		return new String(key);
	}

	String Encrypt(String input) {
		aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
		byte[] Coded;
		char[] Coded2;

		Coded = aesCipher.doFinal(input.getBytes());
		Coded2 = new char[Coded.length];

		for(int i=0;i<Coded.length;i++)
			Coded2[i] = (char)Coded[i];

		return new String(Coded2);

	}

	int Encrypt(int input) {
		aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
		byte[] toCode = ByteBuffer.allocate(4).putInt(input).array();
		byte[] Coded = aesCipher.doFinal(toCode);
		return ByteBuffer.wrap(Coded).getInt();

	}

	byte[] Encrypt(byte[] input) {
		aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
		return aesCipher.doFinal(input);

	}

	void DecryptKey(String input) {
		DateFormat df = new SimpleDateFormat("dd;MM;yy;HH;mm");
		Calendar calobj = Calendar.getInstance();
		String solution = df.format(calobj.getTime());

		char[] key = input.toCharArray();
		int[] iKey = new int[key.length];
		for (int i = 0; i < iKey.length; i++) {
			iKey[i] = (int) key[i];
		}

		for (int i = 0; i < iKey.length; i++) {
			if (i != 0) {
				key[i] = (char) ((((iKey[i]/2) +74) -4)/iKey[i-1]);

			} else {
				char[] cSolution = solution.toCharArray();
				int[] iSolution = new int[cSolution.length];

				for (int j = 0; j < iSolution.length; j++) {
					iSolution[j] = (int) cSolution[j];
				}

				int value = iSolution[0] + iSolution[1] - iSolution[2] + iSolution[3] - iSolution[4];

				key[0] = (char) ((((iKey[i]/2) +74) -4)/value);
			}
		}
		String result =  new String(key);

		aesKey = new SecretKeySpec(result.getBytes(), "AES");
	}

	String Decrypt(String input) {
		aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
		byte[] Coded;
		char[] Coded2;

		Coded = aesCipher.doFinal(input.getBytes());
		Coded2 = new char[Coded.length];
		
		for(int i=0;i<Coded.length;i++)
			Coded2[i] = (char)Coded[i];

		return new String(Coded2);

	}

	int Decrypt(int input) {
		aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
		byte[] toCode = ByteBuffer.allocate(4).putInt(input).array();
		byte[] Coded = aesCipher.doFinal(toCode);
		return ByteBuffer.wrap(Coded).getInt();

	}

	byte[] Decrypt(byte[] input) {
		aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
		return aesCipher.doFinal(input);

	}
}