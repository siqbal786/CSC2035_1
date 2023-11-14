import java.io.*;
import java.net.*;
import java.sql.SQLOutput;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
	DatagramSocket socket;
	static final int RETRY_LIMIT = 4;	/* 
	 * UTILITY METHODS PROVIDED FOR YOU 
	 * Do NOT edit the following functions:
	 *      exitErr
	 *      checksum
	 *      checkFile
	 *      isCorrupted  
	 *      
	 */

	/* exit unimplemented method */
	public void exitErr(String msg) {
		System.out.println("Error: " + msg);
		System.exit(0);
	}	

	/* calculate the segment checksum by adding the payload */
	public int checksum(String content, Boolean corrupted)
	{
		if (!corrupted)  
		{
			int i; 
			int sum = 0;
			for (i = 0; i < content.length(); i++)
				sum += (int)content.charAt(i);
			return sum;
		}
		return 0;
	}


	/* check if the input file does exist */
	File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("SENDER: File does not exists"); 
			System.out.println("SENDER: Exit .."); 
			System.exit(0);
		}
		return file;
	}


	/* 
	 * returns true with the given probability 
	 * 
	 * The result can be passed to the checksum function to "corrupt" a 
	 * checksum with the given probability to simulate network errors in 
	 * file transfer 
	 */
	public boolean isCorrupted(float prob)
	{ 
		double randomValue = Math.random();   
		return randomValue <= prob; 
	}



	/*
	 * The main method for the client.
	 * Do NOT change anything in this method.
	 *
	 * Only specify one transfer mode. That is, either nm or wt
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 5) {
			System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
			System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
			System.err.println("port number: is a positive number in the range 1025 to 65535");
			System.err.println("input file name: is the file to send");
			System.err.println("output file name: is the name of the output file");
			System.err.println("nm selects normal transfer|wt selects transfer with time out");
			System.exit(1);
		}

		Client client = new Client();
		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);
		InetAddress ip = InetAddress.getByName(hostName);
		File file = client.checkFile(args[2]);
		String outputFile =  args[3];
		System.out.println ("----------------------------------------------------");
		System.out.println ("SENDER: File "+ args[2] +" exists  " );
		System.out.println ("----------------------------------------------------");
		System.out.println ("----------------------------------------------------");
		String choice=args[4];
		float loss = 0;
		Scanner sc=new Scanner(System.in);  


		System.out.println ("SENDER: Sending meta data");
		client.sendMetaData(portNumber, ip, file, outputFile); 

		if (choice.equalsIgnoreCase("wt")) {
			System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
			loss = sc.nextFloat();
		} 

		System.out.println("------------------------------------------------------------------");
		System.out.println("------------------------------------------------------------------");
		switch(choice)
		{
		case "nm":
			client.sendFileNormal (portNumber, ip, file);
			break;

		case "wt": 
			client.sendFileWithTimeOut(portNumber, ip, file, loss);
			break; 
		default:
			System.out.println("Error! mode is not recognised");
		} 


		System.out.println("SENDER: File is sent\n");
		sc.close(); 
	}


	/*
	 * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
	 * 
	 * Do not change any method signatures 
	 */

	/* TODO: send metadata (file size and file name to create) to the server 
	 * outputFile: is the name of the file that the server will create
	*/
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) throws IOException {
		long fileSize = file.length();
		String fileName = outputFile;

		MetaData metadata = new MetaData();
		metadata.setName(fileName);
		metadata.setSize((int) fileSize);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
		objectStream.writeObject(metadata);

		byte[] data = outputStream.toByteArray();


		DatagramPacket sentPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
		socket = new DatagramSocket();
		socket.send(sentPacket);

		System.out.println("metadata is sent " + fileName + fileSize + outputFile);


		//exitErr("sendMetaData is not implemented");
	}


	/* TODO: Send the file to the server without corruption*/
	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) throws IOException {

		try {
			//Creates a datagramSocket and binds it to any available port on local machine
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println("the socket could not be opened, or the socket could not bind to the specified port " +
					portNumber);
			System.exit(1);
		}

		int segmentSize = 4;
		byte[] buffer = new byte [segmentSize];
		int bytesRead;
		int sequenceNumber = 0;

		FileInputStream fileInputStream = new FileInputStream(file);

		while ((bytesRead = fileInputStream.read(buffer)) != -1) {
			byte[] payload = new byte[bytesRead];
			System.arraycopy(buffer, 0, payload, 0, bytesRead);
			int checksum = checksum(new String (payload), false);

			Segment segment = new Segment();
			segment.setSize(bytesRead);
			segment.setChecksum(checksum);
			segment.setSq(sequenceNumber);
			segment.setType(SegmentType.Data);
			segment.setPayLoad(new String (payload));

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
			objectStream.writeObject(segment);
			byte[] data = outputStream.toByteArray();

			DatagramPacket packet = new DatagramPacket(data, data.length, IPAddress, portNumber);
			socket.send(packet);
			System.out.println("sending segment " + bytesRead + checksum + sequenceNumber + SegmentType.Data + segment.getPayLoad());
			System.out.println("Waiting for an Ack");

			sequenceNumber++;

			byte[] ackBuffer = new byte[1];
			DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
			socket.receive(ackPacket);
			System.out.println("Ack sq " + sequenceNumber + "RECEIVED.");

			buffer = new byte[segmentSize];
		}

		System.out.println("total segments sent: " + sequenceNumber);
		System.out.println("File sent successfully.");

	}

	//exitErr("sendFileNormal is not implemented");


	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) {

		try {
			//Creates a datagramSocket and binds it to any available port on local machine
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println("the socket could not be opened, or the socket could not bind to the specified port " +
					portNumber);
			System.exit(1);
		}

		int segmentSize = 4;
		byte[] buffer = new byte [segmentSize];
		int bytesRead;
		int sequenceNumber = 0;

		FileInputStream fileInputStream;
		try {
			fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			System.err.println("File not found: " + file.getAbsolutePath());
			socket.close();
			return;
		}

		while (true) {
			try {
				bytesRead = fileInputStream.read(buffer);
				if (bytesRead == -1)
				{
					break;
				}

				byte[] payload = new byte[bytesRead];
				System.arraycopy(buffer, 0, payload, 0, bytesRead);
				int checksum = checksum(new String(payload), isCorrupted(loss));

				Segment segment = new Segment();
				segment.setSize(bytesRead);
				segment.setChecksum(checksum);
				segment.setSq(sequenceNumber);
				segment.setType(SegmentType.Data);
				segment.setPayLoad(new String(payload));

				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
				objectStream.writeObject(segment);
				byte[] data = outputStream.toByteArray();

				DatagramPacket packet = new DatagramPacket(data, data.length, IPAddress, portNumber);
				socket.send(packet);
				System.out.println("sending segment " + bytesRead + checksum + sequenceNumber + SegmentType.Data + segment.getPayLoad());
				System.out.println("Waiting for an Ack");


				byte[] ackBuffer = new byte[1];
				DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);



				try {
					socket.setSoTimeout(1000);
					socket.receive(ackPacket);
					System.out.println("Ack sq " + sequenceNumber + "RECEIVED.");

				} catch (SocketTimeoutException ste) {
					System.out.println("Timeout - resending segment " + sequenceNumber);
					continue;
				}

				sequenceNumber++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("total segments sent: " + sequenceNumber);
		System.out.println("File sent successfully.");

		socket.close();


		//exitErr("sendFileWithTimeOut is not implemented");
	} 


}