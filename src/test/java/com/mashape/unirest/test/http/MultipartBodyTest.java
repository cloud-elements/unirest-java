/*
The MIT License

Copyright (c) 2013 Mashape (http://mashape.com)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.mashape.unirest.test.http;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.body.MultipartBody;

import org.apache.http.HttpEntity;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Wire-shape assertions on the multipart entity produced by {@link MultipartBody}.
 * These tests do not make any HTTP call — they inspect the bytes that would be
 * sent on the wire, so they run in CI without external dependencies and pin the
 * exact behaviour vendors like ServiceNow and Data Fabric depend on.
 */
public class MultipartBodyTest {

	/**
	 * Regression guard for SRE-580487: the outer
	 * {@code Content-Type: multipart/form-data} header must NOT carry a
	 * {@code charset=} parameter. RFC 7578 §4.6 forbids it, and strict
	 * parsers (e.g. ServiceNow's multipart parser) reject the request with
	 * HTTP 400 "File part might be missing" when the parameter is present.
	 *
	 * Calling {@code MultipartEntityBuilder.setCharset(...)} would re-introduce
	 * this parameter; this test pins the absence so any future re-introduction
	 * fails CI rather than a customer.
	 */
	@Test
	public void outerContentTypeHasNoCharsetParameter() {
		MultipartBody body = Unirest.post("http://example.com/")
				.field("name", "Mark")
				.field(
						"file",
						new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
						"test.txt");

		HttpEntity entity = body.getEntity();
		String contentType = entity.getContentType().getValue();

		assertTrue(
				"expected multipart/form-data, got: " + contentType,
				contentType.startsWith("multipart/form-data"));
		assertFalse(
				"outer Content-Type must NOT include a charset parameter (RFC 7578 §4.6); got: " + contentType,
				contentType.toLowerCase().contains("charset"));
	}

	/**
	 * Regression guard for ENGCE-55462: a non-ASCII (Japanese) filename must
	 * survive intact in the rendered multipart body bytes. Under
	 * {@link org.apache.http.entity.mime.HttpMultipartMode#RFC6532} the part
	 * headers are written as UTF-8 directly, so the filename appears
	 * byte-for-byte in the entity stream.
	 *
	 * Without RFC6532 mode (or a UTF-8 charset on the builder) header bytes
	 * fall back to ISO-8859-1 and Japanese characters collapse to '?'.
	 */
	@Test
	public void japaneseFilenameSurvivesInRenderedBody() throws Exception {
		String filename = "日本語.txt";
		MultipartBody body = Unirest.post("http://example.com/")
				.field(
						"file",
						new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
						filename);

		HttpEntity entity = body.getEntity();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		entity.writeTo(out);
		String rendered = new String(out.toByteArray(), StandardCharsets.UTF_8);

		assertTrue(
				"rendered multipart body must contain the literal UTF-8 filename '"
						+ filename + "'; got bytes:\n" + rendered,
				rendered.contains(filename));
		assertFalse(
				"rendered body must not contain '?'-substituted filename (ASCII fallback)",
				rendered.contains("???.txt"));
	}
}
