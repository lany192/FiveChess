package com.lany.fivechess.net;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatContent {
	public ConnectionItem connector;
	public String content;
	public String time;
	public static final String FORMAT;

	static {
		FORMAT = "yyyy-M-d HH:mm:ss";
	}

	public ChatContent(ConnectionItem ci, String content) {
		this.connector = ci;
		this.content = content;
		this.time = new SimpleDateFormat("yyyy-M-d HH:mm:ss", Locale.getDefault())
				.format(new Date());
	}

}
