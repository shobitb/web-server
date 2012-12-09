package ssb402;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Class WebServer contains the main method which is responsible for creating
 * the server socket which listens for requests for clients on a particular
 * port. The port number is taken from the user. Another input from the user is
 * the number of minutes to perform a simulation of the modification of content.
 * The content is simulated to be 'updated' (thus changing the variable
 * lastModified) after every 'update' minutes, which is taken from the user.
 * 
 * After taking the inputs from user, it calls the constructor of the
 * HttpRequest Class and the start() method to begin the request processing.
 * 
 * @author Shobit Beltangdy
 * 
 */
public final class WebServer {

	/**
	 * Accepts inputs from the user and creates the necessary sockets.
	 * 
	 * The server socket will listen for client requests on the port number
	 * provided by the user. The Web Server supports caching and multi-
	 * threading.
	 * 
	 * @param args
	 *            Command-line input, if required
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		System.out
				.println("Waiting for user input for parameter 'update' minutes");
		Float updateTime = Float.parseFloat((new BufferedReader(
				new InputStreamReader(System.in))).readLine());

		Timer timer = new Timer();

		timer.scheduleAtFixedRate(new TimerTask() {
			/*
			 * scheduleAtFixedRate(TimerTask, long, long) function will make the
			 * TimerTask run in the background every updateTime*60000 seconds
			 * with an initial delay of 0. This gives the simulation of
			 * 'modifying' the content every 'updateTime' minutes where
			 * 'updateTime' is user input
			 */
			public void run() {
				HttpRequest.lastModified = HttpRequest.now() + " GMT";
				//System.out.println(HttpRequest.lastModified);
			}
		}, 0, (long) (updateTime * 60000));

		System.out.println("Please enter your choice of port number");
		int portNumber = Integer.parseInt((new BufferedReader(
				new InputStreamReader(System.in))).readLine());

		ServerSocket serverSocket = null;

		/* This can be de-commented while running the TCPClientTester */
		// System.out.println(HttpRequest.lastModified);

		try {
			serverSocket = new ServerSocket(portNumber);
		} catch (IOException e) {
			System.err
					.println("There was a fatal error while listening on port number "
							+ portNumber
							+ ". \nThe error was: "
							+ e.getMessage());
			System.exit(1);
		}

		Socket connection = null;

		try {
			while (true) { // Ensures the server never shuts down, unless there is a fatal error or manually shut down

				connection = serverSocket.accept();
				HttpRequest request = new HttpRequest(connection);
				Thread thread = new Thread(request); // Server supports multithreading
				thread.start();
			}
		} catch (IOException e) {
			System.err
					.println("There was a fatal server error.\nThe error was: "
							+ e.getMessage());
			System.exit(1);
		}

	}

}
