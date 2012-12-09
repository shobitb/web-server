package ssb402;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HttpRequest is the most important class in the Web Server Project that
 * contains a set of methods which perform the task of processing a request from
 * a client and preparing a response for that client. It includes methods that
 * performs these tasks based on the specification as in the RFC 2616 --
 * Hypertext Transfer Protocol -- HTTP/1.1. These methods check syntax of the
 * request line and headers, check that the required fields in the header are
 * present, support caching, generate response with proper syntax, generate a
 * host of errors such as the 304, 400, 404, 500, 505.
 * 
 * 
 * @author Shobit Beltangdy
 * 
 */
public final class HttpRequest implements Runnable {

	private final static String CRLF = "\r\n";;
	private Socket socket;
	private InputStream is;
	DataOutputStream os;
	BufferedReader br;
	private String requestLine;
	private String headerLine;
	private HashMap<String, String> headerMap;
	private boolean matches;
	private String statusLine;
	private String dateServerContentType;
	private String allow;
	private String content;
	private String contentLength;
	private String response;
	private String cacheControl;
	public static String lastModified;

	/**
	 * Initializes a new client socket and other class members.
	 * 
	 * Such as <code>is</code> which is the InputStream of the client socket.<br>
	 * <code>os</code> which is the OutputStream of the client on which the
	 * server writes.<br>
	 * <code>br</code> which is the BufferedReader to create the buffering
	 * character input stream.<br>
	 * <code>headerMap</code> which is the data structure to store the header
	 * field names and values<br>
	 * <code>dateServerContentType</code> which is a concatenated string of
	 * current date, server and content-type used while sending response
	 * 
	 * @param clientSocketArg
	 * @throws Exception
	 */
	public HttpRequest(Socket clientSocketArg) throws Exception {

		socket = clientSocketArg;
		is = socket.getInputStream();
		os = new DataOutputStream(socket.getOutputStream());
		br = new BufferedReader(new InputStreamReader(is));
		headerMap = new HashMap<String, String>();
		dateServerContentType = getDateServerContentType();
	}

	/**
	 * Overrides the <code>run()</code> method of the Runnable interface.
	 * 
	 * Because of this, multiple clients can send requests to the server. Server
	 * supports multi-threading by calling <code>processRequest()</code> each
	 * time a client sends a request.
	 * 
	 * This response message contains the content requested by the client.
	 * 
	 * 
	 */
	public void run() {
		try {
			processRequest();
		} catch (Exception e) {
			/*
			 * This handles the server occuring within the server, for example,
			 * low/out of memory.
			 */
			internalServerError();
		}
	}

	/**
	 * Handles the client's request, processes it, and sends back the response.
	 * 
	 * This function checks for bad inputs and improper syntax, handles various
	 * error cases such as 304, 400, 404, 500, 505. It also sends the 200 OK
	 * response if there are no errors. The high-level logic is as mentioned:
	 * <ul>
	 * <li>Parse the request line and match it with the regular expression based
	 * on Section 5.1 Request-Line of RFC 2616. If the match fails, respond with
	 * a 400 Bad Request and return</li>
	 * <li>Parse the header and match the lines with a regular expression based
	 * on Section 4.2 Message Headers of RFC 2616. If the match fails, respond
	 * with a 400 Bad Request and return.</li>
	 * <li>Check for various other errors such as absence of the "Host: " field,
	 * method not allowed, unsupported HTTP version, and resource not found</li>
	 * <li>If the header contains the 'If-Modified-Since' field, parse it and
	 * match it with a regular expression based on Section 3.3.1 Full Date of
	 * the RFC 2616. If the match fails, respond with a 400 Bad Request and
	 * return.</li>
	 * <li>If the last modified date of the content is same as the
	 * 'If-Modified-Since' field by checking the times (in milliseconds),
	 * respond with a 304 Not Modified and return.
	 * <li>If all errors fail, respond with a 200 OK and send the request
	 * content.</li>
	 * </ul>
	 * 
	 * @throws Exception
	 *             This exception is thrown if there is a fatal error in the
	 *             server causing a 505 Internal Server Error
	 */
	public void processRequest() throws Exception {
		/*
		 * First, check the syntax of the request line. It should match with the
		 * regular expression. According to the Section 5.1 Request-Line of the
		 * RFC 2616 the request line has the following BNF Request-Line = Method
		 * SP Request-URI SP HTTP-Version CRLF
		 */
		String methodRE = "[a-zA-Z]+";
		String requestUriRE = "[A-Za-z0-9\\p{Punct}]+";
		String httpVersionRE = "HTTP\\/[0-9]+\\.[0-9]+";
		String requestLineRE = methodRE + "\\s" + requestUriRE + "\\s"
				+ httpVersionRE;
		requestLine = br.readLine();
		matches = requestLine.matches(requestLineRE);
		if (matches == false) {
			badRequest();
			return;
		}

		/*
		 * Now, check the syntax of the header. It should match with the regular
		 * expression. According to the Section 4.2 Message Headers of the RFC
		 * 2616 the header has the following BNF message-header = field-name ":"
		 * * [ field-value ] field-name = token field-value = *( field-content |
		 * * LWS ) field-content = <the OCTETs making up the field-value and
		 * consisting of either *TEXT or combinations of token, separators, and
		 * quoted-string>
		 */
		StringBuffer sb = new StringBuffer();
		String fieldNameRE = "[A-Za-z0-9\\p{Punct}]+";
		String fieldValueRE = "[A-Za-z0-9\\:\\p{Punct}\\s]*";
		String requestHeaderRE = fieldNameRE + "\\:" + fieldValueRE;
		while ((headerLine = br.readLine()).length() != 0) {
			sb.append(headerLine + CRLF);
			matches = headerLine.matches(requestHeaderRE);
			if (matches == false) {
				badRequest();
				return;
			}

			String[] splitTokens = headerLine.split(":", 2);
			String fieldName = splitTokens[0].toLowerCase();
			
			String fieldValue = splitTokens[1].trim();
			/*
			 * the trim makes sure that leading white spaces are removed as
			 * mentioned in Section 4.2 Message Headers of the RFC 2616
			 */

			headerMap.put(fieldName.toLowerCase(), fieldValue);
		}

		if (!(headerMap.containsKey("host"))) {
			/*
			 * request header MUST contain the "Host: " field as mentioned in
			 * the RFC 2616 Section 5 Request Header
			 */
			badRequest();
			return;
		}

		/*
		 * Following lines of code extract the three parts of a request-line for
		 * error checking
		 */
		StringTokenizer tokens = new StringTokenizer(requestLine);
		String requestMethodToken = tokens.nextToken();
		String requestURIToken = tokens.nextToken();
		String httpVersionToken = tokens.nextToken();

		/* Begin with checking for a 405 Method Not Allowed error. */
		if (!(requestMethodToken.equalsIgnoreCase("GET") || requestMethodToken
				.equalsIgnoreCase("HEAD"))) {
			methodNotAllowed();
			return;
		}

		/* Proceed with checking for the 404 Not Found error */
		if (!(requestURIToken.equals("/TestServer"))) {
			notFound();
			return;
		}

		/* And the 505 HTTP Version Not Supported error */
		if (!(httpVersionToken.equals("HTTP/1.1"))) {
			httpVersionNotSupported();
			return;
		}

		/*
		 * Parse the "If-Modified-Since" field and match it with the regular
		 * expression. The field value must contain the date in Full Date Format
		 * as mentioned in Section 3.3 Date/Time of the RFC 2616. The following
		 * is an example of the full date format: Sun, 06 Nov 1994 08:49:37 GMT
		 */
		if (headerMap.containsKey("if-modified-since")) {
			String val = headerMap.get("if-modified-since");
			String day = "[A-Z][a-z][a-z]";
			String date = "[0-9][0-9]\\s[A-Z][a-z][a-z]\\s[0-9][0-9][0-9][0-9]";
			String time = "[0-9][0-9]\\:[0-9][0-9]\\:[0-9][0-9]";
			String zone = "GMT";

			matches = val.matches(day + "\\,\\s" + date + "\\s" + time + "\\s"
					+ zone);

			if (matches == false) {
				badRequest();
				return;
			}

			/*
			 * This part handles caching. If the "If-Modified-Since" value is
			 * equal to the value of time of last modification of resource
			 * requested, then the server will not explicitly send any content.
			 */
			String ifModifiedSince = headerMap.get("if-modified-since");
			if (ifModifiedSince.equals(lastModified)) {
				notModified();
				return;
			}
		}

		/* When there are no errors, send the proper response, of course! */
		sendResponse();
	}

	/**
	 * Generates the current time in GMT format.
	 * 
	 * The method is declared <code>static</code> to make it accessible without
	 * having to create an object of Class HttpRequest.
	 * 
	 * @return a String in the format <code>Day, dd-MM-yyyy HH:mm:ss</code>.
	 *         This is the standard format of time as mentioned in Section 3.3
	 *         of RFC 2616
	 */
	public static String now() {
		Calendar c = Calendar.getInstance();

		DateFormat df = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss");
		/*
		 * For example, Fri, 28 Oct 2011 05:04:30 GMT
		 */
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		String dateToday = df.format(c.getTime());
		return dateToday;
	}

	/**
	 * Returns a combination of the header fields Date, Server and Content-Type.
	 * 
	 * The method is created to avoid unneccesary duplication of code in each of
	 * the methods used for generating response.
	 * 
	 * @return a String which is the combination of current date, server, and
	 *         content-type
	 */
	public String getDateServerContentType() {
		/*
		 * This is to avoid code duplication in each of the response-handling
		 * methods defined below this method
		 */
		String dateToday = "Date: " + now() + " GMT " + CRLF;
		String server = "Server: Data Communications & Networks: HTTP Server by Shobit Beltangdy"
				+ CRLF;
		String contentType = "Content-Type: text/html" + CRLF;

		return dateToday + server + contentType;
	}

	/**
	 * Generates a 400 Bad Request server response.
	 * 
	 * This response is sent when the request has a bad syntax.
	 */
	public void badRequest() {
		/*
		 * This method is called every time there is an improper syntax in the
		 * request, including the request-line, the header lines, the
		 * If-Modified-Since field value to name a few
		 */
		try {
			statusLine = "HTTP/1.1 400 Bad Request" + CRLF;
			content = "<html><body><h1>The request could not be understood by the server due to malformed syntax</h1></body></html>";
			contentLength = "Content-length: " + content.length() + CRLF;

			response = statusLine + dateServerContentType + contentLength
					+ CRLF + content;

			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (IOException e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}

	}

	/**
	 * Generates a 404 Not Found server response.
	 * 
	 * This response is sent when the server cannot find the requested resource.
	 */
	public void notFound() {
		/*
		 * This method is called when the URI is anything different than
		 * /TestServer which is what this particular Server supports.
		 */

		statusLine = "HTTP/1.1 404 Not Found" + CRLF;
		content = "<html><body><h1>The server has not found anything matching the Request-URI.</h1></body></html>";
		contentLength = "Content-length: " + content.length() + CRLF;

		response = statusLine + dateServerContentType + contentLength + CRLF
				+ content;

		try {
			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (IOException e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}

	}

	/**
	 * Generates a 505 HTTP Version Not Supported server response.
	 * 
	 * This response message is generated only when the HTTP Version used by the
	 * client is not HTTP/1.1.
	 */
	public void httpVersionNotSupported() {
		/*
		 * This method is called if the HTTP version is anything other than 1.1
		 * because this particular Server only supports HTTP/1.1
		 */
		statusLine = "HTTP/1.1 505 HTTP Version Not Supported" + CRLF;
		content = "<html><body><h1>The server does not support the HTTP protocol version that was used in the request message</h1></body></html>";
		contentLength = "Content-length: " + content.length() + CRLF;

		response = statusLine + dateServerContentType + contentLength + CRLF
				+ content;

		try {
			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (IOException e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}
	}

	/**
	 * Generates a 405 Method Not Allowed server response.
	 * 
	 * This response message is generated only when the request line does not
	 * contain "GET" or "HEAD".
	 */
	public void methodNotAllowed() {
		/*
		 * This method is called when the request-line contains a method other
		 * than GET or HEAD because this particular Server only supports GET and
		 * HEAD
		 */
		statusLine = "HTTP/1.1 405 Method Not Allowed" + CRLF;
		allow = "GET, HEAD" + CRLF;
		content = "<html><body><h1>The method specified in the Request-Line is not allowed for the resource identified by the Request-URI.</h1></body></html>";
		contentLength = "Content-length: " + content.length() + CRLF;

		response = statusLine + dateServerContentType + allow + contentLength
				+ CRLF + content;

		try {
			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (IOException e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}
	}

	/**
	 * Generates a 304 Not Modified server response.
	 * 
	 * This response message is generated only when the content requested by the
	 * client has not been modified since the client's last request.
	 */
	public void notModified() {
		/*
		 * This method is called when the last modified time of the resource
		 * requested matches with the If-Modified-Since field from the request,
		 * which means that the resource requested was not modified since the
		 * last time the request was made.
		 */
		String lastMod = "Last-Modified: " + lastModified + " GMT " + CRLF;
		statusLine = "HTTP/1.1 304 Not Modified" + CRLF;

		/*
		 * As per Section 10.3.5 304 Not Modified of the RFC 2616, the server
		 * should not send the message-body explicitly when returning 304 Not
		 * Modified
		 */
		response = statusLine + dateServerContentType + lastMod + CRLF;

		try {
			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (IOException e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}

	}

	/**
	 * Generates a 200 OK server response.
	 * 
	 * This response message contains the content requested by the client.
	 */
	public void sendResponse() {
		/*
		 * If all goes well, this method will be called to send the correct
		 * response containing the message-body. The message-body will be sent
		 * if the method is GET and not sent if the method is HEAD as mentioned
		 * in Sections 9.3 GET and 9.4 HEAD of the RFC 2616
		 */
		try {
			statusLine = "HTTP/1.1 200 OK" + CRLF;
			content = "<html><head><body>I was created by Shobit Beltangdy. My server is listening on "
					+ InetAddress.getLocalHost()
					+ ": "
					+ socket.getLocalPort()
					+ ". It is now "
					+ now()
					+ " GMT "
					+ "<br>This object was created at "
					+ lastModified
					+ " GMT.</body></html>";
			contentLength = "Content-length: " + content.length() + CRLF;
			cacheControl = "Cache-Control: public" + CRLF;

			// do not send content if request method is HEAD
			if (requestLine.startsWith("HEAD"))
				response = statusLine + dateServerContentType + CRLF;
			// send the content if the request method is GET
			else
				response = statusLine + dateServerContentType + cacheControl
						+ contentLength + CRLF + content;

			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();

		} catch (Exception e) {

			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}
	}

	/**
	 * Generates a 500 Internal Server Error server response.
	 * 
	 * This response message is generated only when there is an error within the
	 * server, for example, out of memory.
	 * 
	 */
	public void internalServerError() {
		/*
		 * This will be called when there is an exception while trying to call
		 * the processRequest() method
		 */
		statusLine = "HTTP/1.1 500 Internal Server Error" + CRLF;
		content = "<html><body><h1>500 Internal Server Error</h1></body></html>";
		contentLength = "Content-length: " + content.length() + CRLF;
		response = statusLine + dateServerContentType + contentLength + CRLF
				+ content;
		try {
			os.writeBytes(response);
			os.close();
			br.close();
			socket.close();
		} catch (IOException e) {
			System.err
					.println("There was an error trying to communicate with the client.\nThe error was: "
							+ e.getMessage());
		}

	}

}
