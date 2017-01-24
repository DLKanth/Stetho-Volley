package me.dlkanth.stethovolley;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.HttpStack;
import com.facebook.stetho.urlconnection.ByteArrayRequestEntity;
import com.facebook.stetho.urlconnection.SimpleRequestEntity;
import com.facebook.stetho.urlconnection.StethoURLConnectionManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lakshmikanth on 1/25/2017.
 */

public class StethoVolleyStack implements HttpStack {


    @Override
    public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {

        String friendlyName = System.currentTimeMillis() + "";
        StethoURLConnectionManager connectionManager = new StethoURLConnectionManager(friendlyName);

        String urlString = request.getUrl();
        HashMap<String, String> map = new HashMap<String, String>();
        map.putAll(request.getHeaders());
        map.putAll(additionalHeaders);

        URL url = new URL(urlString);

        HttpURLConnection connection = openConnection(url, request);

        for (String headerName : map.keySet()) {
            connection.addRequestProperty(headerName, map.get(headerName));
        }
        connection.addRequestProperty("Accept-Encoding","gzip");
        boolean isPreConnected = setConnectionParametersForRequest(connectionManager, connection, request);

        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);

        if (!isPreConnected) {
            preConnect(connectionManager,connection,null);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == -1) {
            throw new IOException("Could not retrieve response code from HttpUrlConnection.");
        }

        StatusLine responseStatus = new BasicStatusLine(protocolVersion,
                connection.getResponseCode(), connection.getResponseMessage());
        BasicHttpResponse response = new BasicHttpResponse(responseStatus);

        postConnect(connectionManager);

        if (hasResponseBody(request.getMethod(), responseStatus.getStatusCode())) {
            response.setEntity(entityFromConnection(connection));
        }
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null) {
                Header h = new BasicHeader(header.getKey(), header.getValue().get(0));
                response.addHeader(h);
            }
        }

        return response;
    }

    protected HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());
        return connection;
    }

    private void preConnect(StethoURLConnectionManager manager, HttpURLConnection connection, SimpleRequestEntity requestEntity) {
        manager.preConnect(connection, requestEntity);
    }

    private void postConnect(StethoURLConnectionManager manager) {
        try {
            manager.postConnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SimpleRequestEntity formRequestEntity(byte[] requestBody) {
        SimpleRequestEntity requestEntity = new ByteArrayRequestEntity(requestBody);
        return requestEntity;
    }

    private HttpURLConnection openConnection(URL url, Request<?> request) throws IOException {
        HttpURLConnection connection = createConnection(url);

        int timeoutMs = request.getTimeoutMs();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setDoInput(true);

        return connection;
    }

    private static boolean hasResponseBody(int requestMethod, int responseCode) {
        return requestMethod != Request.Method.HEAD
                && !(HttpStatus.SC_CONTINUE <= responseCode && responseCode < HttpStatus.SC_OK)
                && responseCode != HttpStatus.SC_NO_CONTENT
                && responseCode != HttpStatus.SC_NOT_MODIFIED;
    }

    private static HttpEntity entityFromConnection(HttpURLConnection connection) {
        BasicHttpEntity entity = new BasicHttpEntity();
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        entity.setContent(inputStream);
        entity.setContentLength(connection.getContentLength());
        entity.setContentEncoding(connection.getContentEncoding());
        entity.setContentType(connection.getContentType());
        return entity;
    }

    // changing this method to handle stetho connection manager
    // return true if stetho preconnected
    private boolean setConnectionParametersForRequest(StethoURLConnectionManager connectionManager, HttpURLConnection connection,
                                                  Request<?> request) throws IOException, AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                byte[] postBody = request.getPostBody();
                if (postBody != null) {
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.addRequestProperty("Content-Type", request.getPostBodyContentType());

                    preConnect(connectionManager, connection, null);

                    DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                    out.write(postBody);
                    out.close();
                }
                return true;
            case Request.Method.GET:
                connection.setRequestMethod("GET");
                return false;
            case Request.Method.DELETE:
                connection.setRequestMethod("DELETE");
                return false;
            case Request.Method.POST:
                connection.setRequestMethod("POST");
                return addBodyIfExists(connectionManager, connection, request);
            case Request.Method.PUT:
                connection.setRequestMethod("PUT");
                return addBodyIfExists(connectionManager, connection, request);
            case Request.Method.HEAD:
                connection.setRequestMethod("HEAD");
                return false;
            case Request.Method.OPTIONS:
                connection.setRequestMethod("OPTIONS");
                return false;
            case Request.Method.TRACE:
                connection.setRequestMethod("TRACE");
                return false;
            case Request.Method.PATCH:
                connection.setRequestMethod("PATCH");
                return addBodyIfExists(connectionManager, connection, request);
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private boolean addBodyIfExists(StethoURLConnectionManager connectionManager, HttpURLConnection connection, Request<?> request)
            throws IOException, AuthFailureError {
        byte[] body = request.getBody();
        if (body != null) {
            connection.setDoOutput(true);
            connection.addRequestProperty("Content-Type", request.getBodyContentType());

            connectionManager.preConnect(connection, formRequestEntity(body));

            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.write(body);
            out.close();
            return true;
        }
        return false;
    }
}
