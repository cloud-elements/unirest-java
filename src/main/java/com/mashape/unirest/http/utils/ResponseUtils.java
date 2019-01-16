package com.mashape.unirest.http.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.options.Option;
import com.mashape.unirest.http.options.Options;
import org.apache.http.Header;
import org.apache.http.HeaderElement;

public class ResponseUtils {

	private static final Pattern charsetPattern = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");

	/**
	 * Parse out a charset from a content type header.
	 * 
	 * @param contentType e.g. "text/html; charset=EUC-JP"
	 * @return "EUC-JP", or null if not found. Charset is trimmed and uppercased.
	 */
	public static String getCharsetFromContentType(String contentType) {
		if (contentType == null)
			return null;

		Matcher m = charsetPattern.matcher(contentType);
		if (m.find()) {
			return m.group(1).trim().toUpperCase();
		}
		return null;
	}

	public static boolean isMultipartContentType(Header contentType) {
		if (contentType == null)
			return false;

		if (contentType.getValue().toLowerCase().contains("multipart")) {
			return true;
		}

		HeaderElement[] elements = contentType.getElements();
		if (elements == null) {
			return false;
		}
		for (HeaderElement element : elements) {
			if (element == null || element.getValue() == null) { continue; }
			if (element.getValue().toLowerCase().contains("multipart")) {
				return true;
			}
		}

		return false;
	}

	public static void checkResponseSize(Long contentLength) throws IOException {
		Number maxResponse = (Number) Options.getOption(Option.MAX_RESPONSE_SIZE);
		if (maxResponse == null) {
			return;
		}
		long maxAsLong= maxResponse.longValue();
		if (maxAsLong > -1 && maxAsLong < contentLength) {
			throw new IOException("Declared response content too large: " + maxResponse + " > " + contentLength);
		}
	}

	public static byte[] getBytes(InputStream is, boolean checkSize) throws IOException {
		int len;
		int size = 1024;
		byte[] buf;

		if (is instanceof ByteArrayInputStream) {
			size = is.available();
			if (checkSize) {
				ResponseUtils.checkResponseSize((long) size);
			}
			buf = new byte[size];
			len = is.read(buf, 0, size);
		} else {
			long total = 0;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			buf = new byte[size];
			// For efficiency, allow single page overflows past maxResponseSize
			// (i.e., don't limit `size = maxResponse - total` or anything like that).
			while ((len = is.read(buf, 0, size)) != -1) {
				total += len;
				if (checkSize) {
					ResponseUtils.checkResponseSize(total);
				}
				bos.write(buf, 0, len);
			}
			buf = bos.toByteArray();
		}
		return buf;
	}

	public static boolean isGzipped(Header contentEncoding) {
		if (contentEncoding != null) {
			String value = contentEncoding.getValue();
			if (value != null && "gzip".equals(value.toLowerCase().trim())) {
				return true;
			}
		}
		return false;
	}

}
