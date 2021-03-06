package com.dbox.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import com.dbox.client.Login.LoggedIn;
import com.dbox.client.Password.UpdatePassword;

public class WebService
{	
	public static Resource[] get ( String url, int port, String username, String password ) throws Exception
	{
		try
		{
			// Parse out the domain from the given URL. 
			String domain = new URL(url).getHost();

			// Create a connection to the server.
			DefaultHttpClient httpclient = getClient(domain,port,username,password);

			// Send the request and receive the response.
			HttpGet httpget = new HttpGet(url);
			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			
			int statusCode = response.getStatusLine().getStatusCode();

			// Check that login was successful.
			if (statusCode == 200)
			{
				InputStream responseStream = entity.getContent();
				
				byte[] x = IOUtils.toByteArray(responseStream);
				
				responseStream.close();
				responseStream = null;
				
				if (entity != null)
				{
					entity.consumeContent();
				}
				
				httpclient = null;
				httpget = null;
				response = null;
				entity = null;
				domain = null;
				url = null;
				username = null;
				password = null;
				
				return XmlEngine.xmlToResource(x);
			}
			else if (statusCode == 401 || statusCode == 403)
			{
				throw new LoginException();
			}

			// HttpEntity instance is no longer needed, so we signal that resources 
			// should be deallocated
			if (entity != null)
			{
				entity.consumeContent();
			}

			// HttpClient instance is no longer needed, so we shut down the connection manager
			// to ensure the immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();
		}
		catch(LoginException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			if (e instanceof URISyntaxException || e instanceof IOException)
			{
				throw new HttpException();
			}
			throw e;
		}
		
		return null;
	}
	
	public static boolean delete ( String url, int port, String username, String password ) throws Exception
	{
		try
		{
			// Parse out the domain from the given URL. 
			String domain = new URL(url).getHost();

			// Create a connection to the server.
			DefaultHttpClient httpclient = getClient(domain,port,username,password);

			// Send the request and receive the response.
			HttpDelete delete = new HttpDelete(url);
			HttpResponse response = httpclient.execute(delete);
			HttpEntity entity = response.getEntity();

			int statusCode = response.getStatusLine().getStatusCode();

			// Check that login was successful.
			if (statusCode == 200)
			{
				InputStream responseStream = entity.getContent();
				ByteArrayOutputStream responseData = new ByteArrayOutputStream();
				int ch;

				// read the server response
				while ((ch = responseStream.read()) != -1)
				{
					responseData.write(ch);
				}
				
				return true;
			}
			else if (statusCode == 401 || statusCode == 403)
			{
				throw new LoginException();
			}

			// HttpEntity instance is no longer needed, so we signal that resources 
			// should be deallocated
			if (entity != null)
			{
				entity.consumeContent();
			}

			// HttpClient instance is no longer needed, so we shut down the connection manager
			// to ensure the immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();
		}
		catch(Exception e)
		{
			throw e;
		}
		
		return false;
	}

	public static boolean put ( String url, int port, String username, String password, String body) throws Exception
	{
		try
		{
			// Parse out the domain from the given URL. 
			String domain = new URL(url).getHost();

			// Create a connection to the server.
			DefaultHttpClient httpclient = getClient(domain,port,username,password);

			// Send the request and receive the response.
			HttpPut httpput = new HttpPut(url);
			StringEntity x = new StringEntity(body);
			httpput.setEntity(x);
			
			HttpResponse response = httpclient.execute(httpput);
			HttpEntity entity = response.getEntity();

			int statusCode = response.getStatusLine().getStatusCode();

			// Check that the upload was successful.
			if (statusCode == 200)
			{
				return true;
			}
			else if (statusCode == 401 || statusCode == 403)
			{
				throw new LoginException();
			}

			// HttpEntity instance is no longer needed, so we signal that resources 
			// should be deallocated
			if (entity != null)
			{
				entity.consumeContent();
			}

			// HttpClient instance is no longer needed, so we shut down the connection manager
			// to ensure the immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();
		}
		catch(Exception e)
		{
			throw e;
		}
		
		return false;
	}

	public static LoggedIn register(String host, int port, String username, String password)
	{
		try
		{
	        URL u = new URL(host);
	        String domain = u.getHost();
	
	        DefaultHttpClient httpclient = new DefaultHttpClient();
	        
	    	String url = ("http://" + domain + ":" + port + "/register");
	
			// Create a connection to the server.
			StringEntity xmlmsg = new StringEntity("<Registration>\n"+
	        						"<Username>"+username+"</Username>\n"+
	        						"<Password>"+password+"</Password>\n"+
	        						"</Registration>");
	
			// Send the request and receive the response.
	    	HttpPut put = new HttpPut(url);
	    	put.setEntity(xmlmsg);
			HttpResponse response = httpclient.execute(put);
	
			int statusCode = response.getStatusLine().getStatusCode();
	
			httpclient.getConnectionManager().shutdown();
			
			// Check that registration was successful. It fails if the user already exists
			if (statusCode == 200)
			{
				return Login.LoggedIn.SUCCESS;
			}
			else 
			{
				return Login.LoggedIn.FAILURE;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return Login.LoggedIn.ERROR;
		}
	}
	
	public static UpdatePassword update_password(String host, int port, String username, String password, String newPassword)
	{
		try
		{
	        URL u = new URL(host);
	        String domain = u.getHost();
	
	        // Create a connection to the server.
			DefaultHttpClient httpclient = getClient(domain,port,username,password);
	        
	    	String url = ("http://" + domain + ":" + port + "/password");
	
			// Create a connection to the server.
			StringEntity xmlmsg = new StringEntity("<Registration>\n"+
	        						"<Password>"+newPassword+"</Password>\n"+
	        						"</Registration>");
	
			// Send the request and receive the response.
	    	HttpPut put = new HttpPut(url);
	    	put.setEntity(xmlmsg);
			HttpResponse response = httpclient.execute(put);
	
			int statusCode = response.getStatusLine().getStatusCode();
	
			httpclient.getConnectionManager().shutdown();
			
			if (statusCode == 200)
			{
				return Password.UpdatePassword.SUCCESS;
			}
			else 
			{
				return Password.UpdatePassword.FAILURE;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return UpdatePassword.ERROR;
		}
	}
	
	public static DefaultHttpClient getClient(String domain, int port, String username, String password ) throws Exception
	{        
        // Create a connection to the server.
    	DefaultHttpClient httpclient = new DefaultHttpClient();
    	httpclient.getCredentialsProvider().setCredentials
    	(
            new AuthScope(domain, port),
            new UsernamePasswordCredentials(username, password)
    	);
    	
    	return httpclient;
	}
}
