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

package com.mashape.unirest.request.body;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;

import com.mashape.unirest.http.utils.MapUtil;
import com.mashape.unirest.request.BaseRequest;
import com.mashape.unirest.request.HttpRequest;

public class MultipartBody extends BaseRequest implements Body {

	private List<String> keyOrder = new ArrayList<String>();
	private Map<String, List<Object>> parameters = new HashMap<String, List<Object>>();
	private Map<String, List<ContentType>> contentTypes = new HashMap<String, List<ContentType>>();
	private Map<String, List<String>> fileNames = new HashMap<String, List<String>>();

	private boolean hasFile;
	private HttpRequest httpRequestObj;
	private HttpMultipartMode mode;

	public MultipartBody(HttpRequest httpRequest) {
		super(httpRequest);
		this.httpRequestObj = httpRequest;
	}

	public MultipartBody field(String name, String value) {
		return field(name, value, false, null, null);
	}

	public MultipartBody field(String name, String value, String contentType) {
		return field(name, value, false, null, contentType);
	}

	public MultipartBody field(String name, Collection<?> collection) {
		for(Object current : collection) {
			boolean isFile = current instanceof File;
			field(name, current, isFile, null, null);
		}
		return this;
	}

	public MultipartBody field(String name, Object value) {
		return field(name, value, false, null, null);
	}

	public MultipartBody field(String name, Object value, boolean file) {
		return field(name, value, file, null, null);
	}

	public MultipartBody field(String name, Object value, boolean file, String contentType) {
		return field(name, value, file, null, contentType);
	}

	public MultipartBody field(String name, Object value, boolean file, String fileName, String contentType) {
		keyOrder.add(name);

		List<Object> list = parameters.get(name);
		if (list == null) list = new LinkedList<Object>();
		list.add(value);
		parameters.put(name, list);

		ContentType type;
		if (contentType != null && !contentType.isEmpty()) { type = ContentType.parse(contentType); }
		else if (file) { type = ContentType.APPLICATION_OCTET_STREAM; }
		else { type = ContentType.APPLICATION_FORM_URLENCODED.withCharset(UTF_8); }
		List<ContentType> types = contentTypes.get(name);
		if (types == null) types = new LinkedList<ContentType>();
		types.add(type);
		contentTypes.put(name, types);

		List<String> fns = fileNames.get(name);
		if (fns == null) fns = new LinkedList<String>();
		fns.add(fileName);
		fileNames.put(name, fns);

		if (!hasFile && file) {
			hasFile = true;
		}

		return this;
	}

	public MultipartBody field(String name, File file) {
		return field(name, file, true, null, null);
	}

	public MultipartBody field(String name, File file, String contentType) {
		return field(name, file, true, null, contentType);
	}

	public MultipartBody field(String name, InputStream file, String fileName) {
		return field(name, file, true, fileName, null);
	}

	public MultipartBody field(String name, InputStream file, String fileName, String contentType) {
		return field(name, file, true, fileName, contentType);
	}

	public MultipartBody basicAuth(String username, String password) {
		httpRequestObj.basicAuth(username, password);
		return this;
	}

	public MultipartBody mode(String mode) {
		this.mode = HttpMultipartMode.valueOf(mode);
		return this;
	}

	public HttpEntity getEntity() {
		if (hasFile) {
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			if (mode == null) { builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE); }
			else { builder.setMode(mode); }
			Set<String> visitedKeys = new HashSet<String>();
			for(String key: keyOrder) {
				if (visitedKeys.contains(key)) { continue; }
				visitedKeys.add(key);
				List<Object> value = parameters.get(key);
				List<ContentType> types = contentTypes.get(key);
				List<String> fns = fileNames.get(key);
				for(int i = 0; i < value.size(); i++) {
					Object cur = value.get(i);
					ContentType contentType = types.get(i);
					String fileName = fns.get(i);
					if (cur instanceof File) {
						File file = (File) cur;
						builder.addPart(key, new FileBody(file, contentType, file.getName()));
					} else if (cur instanceof InputStream) {
						InputStream file = (InputStream) cur;
						builder.addPart(key, new InputStreamBody(file, contentType, fileName));
					} else {
						builder.addPart(key, new StringBody(cur.toString(), contentType));
					}
				}
			}
			return builder.build();
		} else {
			try {
				return new UrlEncodedFormEntity(MapUtil.getList(parameters), UTF_8);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
