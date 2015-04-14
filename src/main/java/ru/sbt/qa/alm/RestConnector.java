package ru.sbt.qa.alm;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;




public class RestConnector {
	String baseurl,domain,project;
	String resturl;
	CloseableHttpClient client;
	HttpClientContext context;
	Logger logger = LoggerFactory.getLogger(RestConnector.class);
	JAXBContext jaxbctx;
	Marshaller marshaller;
	Unmarshaller unmarshaller;
	RequestConfig rqcnf;
	public RestConnector(String url,String domain,String project) throws JAXBException {
		this.baseurl=url;
		this.domain=domain;
		this.project=project;
		resturl=baseurl+"domains/"+this.domain+"/projects/"+this.project+"/";
		client = HttpClients.createDefault();
		rqcnf = RequestConfig.custom().setCookieSpec(CookieSpecs.BEST_MATCH)
				.setExpectContinueEnabled(true)
				.setStaleConnectionCheckEnabled(true)
				.setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
				.setSocketTimeout(5000)
				.setConnectTimeout(5000)
				.setConnectionRequestTimeout(5000)
				.build();
		jaxbctx = JAXBContext.newInstance(Entities.class);
		marshaller=jaxbctx.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		unmarshaller=jaxbctx.createUnmarshaller();
	}
	
	public boolean login(String username,String password) {
		String unamepass=username+":"+password;
		String authstr = Base64.getEncoder().encodeToString(unamepass.getBytes());
		HttpGet authget = new HttpGet(resturl+"/is-authenticated");
		HttpPost sespost = new HttpPost(baseurl+"/site-session");
		authget.setConfig(rqcnf);
		try {
			HttpResponse resp = client.execute(authget);
			if(resp.getStatusLine().getStatusCode()==HttpURLConnection.HTTP_OK) {
				logger.debug("Already authenticated");
				return true;
			}
			authget.releaseConnection();
			String wwwauth = resp.getFirstHeader("WWW-Authenticate").getValue();
			if(wwwauth!=null) {
				String[] authparts = wwwauth.split("=\"");
				wwwauth = authparts[1].substring(0,authparts[1].length()-1);
			} else {
				logger.debug("noe www-authenticate header, using rest url as auth point");
				wwwauth=resturl;
			}
			authget=new HttpGet(wwwauth+"/authenticate");
			authget.setConfig(rqcnf);
			authget.addHeader("Authorization","Basic "+authstr);
			resp = client.execute(authget);
			if(resp.getStatusLine().getStatusCode()!=HttpURLConnection.HTTP_OK) {
				logger.debug("Authentication failed");
				logger.debug(resp.getStatusLine().toString());
				logger.debug(Arrays.toString(resp.getAllHeaders()));
				return false;
			}
			authget.releaseConnection();
			logger.debug("Authentication successfull");
			//request and store QCSession
			resp = client.execute(sespost);
			logger.debug("QCSession is stored");
			logger.debug(resp.getStatusLine().toString());
			return true;
		} catch (IOException e) {
			logger.debug("login exception", e);
			return false;
		} finally {
			authget.releaseConnection();
			sespost.releaseConnection();
		}
	}
	
	public String get(String enturl,Map<String,String> params) {
		String url=resturl+enturl;
		HttpGet req=null;
		try {
			List<NameValuePair> nvps = new ArrayList<>();
			for(Entry<String,String> ent : params.entrySet()) {
				nvps.add(new BasicNameValuePair(ent.getKey(), ent.getValue()));
			}
			URI uri = new URIBuilder(url).addParameters(nvps).build();
			req = new HttpGet(uri);
			req.setConfig(rqcnf);
			HttpResponse resp = client.execute(req);
			int code = resp.getStatusLine().getStatusCode();
			if(code==HttpURLConnection.HTTP_OK) {
				HttpEntity ent = resp.getEntity();
				String entContents = EntityUtils.toString(ent);
				return entContents;
			}
			return null;
		} catch (URISyntaxException | IOException e) {
			logger.debug("execRequest exception "+url, e);
			return null;
		} finally {
			if(req!=null) req.releaseConnection();
		}
		
		
	}
	
	public boolean post(String url,String data) {
		String posturi=resturl+url;
		HttpPost p = new HttpPost(posturi);
		return execRequest(p,data);
	}
	
	public boolean put(String url,String data) {
		String posturi=resturl+url;
		HttpPut p = new HttpPut(posturi);
		return execRequest(p,data);
	}
	
	public boolean execRequest(HttpEntityEnclosingRequest req,String data) {
		try {
			req.setEntity(new StringEntity(data));
			HttpResponse resp = client.execute((HttpUriRequest) req);
			logger.debug("request:"+req.getRequestLine().toString());
			logger.debug(resp.getStatusLine().toString());
			logger.debug(Arrays.toString(resp.getAllHeaders()));
			int code = resp.getStatusLine().getStatusCode();
			return (code>=200 && code<=399); 			
		} catch (IOException e) {
			logger.debug("execRequest exception "+req.getRequestLine().toString(), e);
			return false;
		} 
	}
	
	public <T> T unmarshall(String content,Class<T> cls) {
		if(content==null) return null;
		try {
			JAXBElement<T> ent = unmarshaller.unmarshal(new StreamSource(new StringReader(content)),cls);
			return ent.getValue();
		} catch(JAXBException e) {
			logger.debug("unmarshall "+cls.getCanonicalName()+" exception", e);
			return null;
		}
	}
	
	public <T> String marshall(T obj) {
		StringWriter wrt = new StringWriter();
		try {
			marshaller.marshal(obj, wrt);
			return wrt.toString();
		} catch(JAXBException e) {
			logger.debug("marshall "+obj.toString()+" exception", e);
			return null;
		}
	}
	
	
	public Entity getEntity(String url,Map<String,String> params) {
		String content = get(url,params);
		return unmarshall(content, Entity.class);
	}
	
	public Entities getEntities(String url,Map<String,String> params) {
		String content = get(url,params);
		return unmarshall(content, Entities.class);
	}
	
	public <T> boolean postObj(String url,T obj) {
		String content = marshall(obj);
		if(content==null) return false;
		return post(url,content);
	}
	
	public <T> boolean putObj(String url,T obj) {
		String content = marshall(obj);
		if(content==null) return false;
		return put(url,content);
	}

}
