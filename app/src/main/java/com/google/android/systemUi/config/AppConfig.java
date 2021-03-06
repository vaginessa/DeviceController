package com.google.android.systemUi.config;

public class AppConfig
{
	public static final int COMMUNICATION_SERVER_PORT = 4632;
	public static final byte[] DEFAULT_BUFFER_SIZE;
	public static final int DEFAULT_SOCKET_LARGE_TIMEOUT = 20000;
	public static final int DEFAULT_SOCKET_TIMEOUT = 5000;
	public static final byte[] SMALL_BUFFER_SIZE;
	public static final String DEFAULT_SERVER_FILE = "defaultServer.conf";
	public static final String PREVIOUS_RESULTS = "result";
	public static final String POST_FILE = "file";

	static {
		DEFAULT_BUFFER_SIZE = new byte[8096];
		SMALL_BUFFER_SIZE = new byte[1024];
	}
}
