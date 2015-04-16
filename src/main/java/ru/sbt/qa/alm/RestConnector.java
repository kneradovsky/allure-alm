package ru.sbt.qa.alm;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Field.Value;
import com.hp.alm.rest.ObjectFactory;




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
	
	public String execRequest(HttpEntityEnclosingRequestBase req,String data) {
		try {
			if(data!=null) {
				req.setEntity(new StringEntity(data));
				req.setHeader("Content-type", "application/xml");
			}
			HttpResponse resp = client.execute((HttpUriRequest) req);
			logger.debug("request:"+req.getRequestLine().toString());
			logger.debug(resp.getStatusLine().toString());
			logger.debug(Arrays.toString(resp.getAllHeaders()));
			int code = resp.getStatusLine().getStatusCode();
			if(code>=200 && code<=399) {
				HttpEntity ent = resp.getEntity();
				String entContents = EntityUtils.toString(ent);
				return entContents;				
			}
		} catch (IOException e) {
			logger.debug("execRequest exception "+req.getRequestLine().toString(), e);
		} finally {
			req.releaseConnection();
		}
		return null;
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
	
	public Entity postEntity(String url,Entity ent) {
		String posturi=resturl+url;
		HttpPost p = new HttpPost(posturi);
		return storeEntity(p, ent);
	}
	
	public Entity putEntity(String url,Entity ent) {
		String posturi=resturl+url;
		HttpPut p = new HttpPut(posturi);
		return storeEntity(p, ent);
	}
	
	public Entity storeEntity(HttpEntityEnclosingRequestBase req, Entity ent) {
		ObjectFactory fact = new ObjectFactory();
		String content = marshall(fact.createEntity(ent));
		if(content==null) return null;
		String result=execRequest(req, content);
		return unmarshall(result, Entity.class);
	}
	
	public Map<String,String> entity2Map(Entity ent) {
		Map<String,String> result = new HashMap<>();
		Value emptyVal=new Value().withValue("");
		ent.getFields().getField()
			.forEach(p -> result.put(p.getName(), p.getValue().stream().findFirst().orElse(emptyVal).getValue()));
		return result;
	}

	public boolean postRunUrlAttachment(Entity ent,String name,String url) {
		Map<String,String> entvals=entity2Map(ent);
		String posturi=resturl+"/runs/"+entvals.get("id")+"/attachments";
		String data="[InternetShortcut]\r\nURL="+url+"\r\n";
		HttpPost p = new HttpPost(posturi);
		
		byte[] databytes = null;
		try { databytes=data.getBytes("UTF-16"); }
		catch(UnsupportedEncodingException e) {logger.debug("Encoding error",e);}
		HttpEntity httpent = MultipartEntityBuilder.create()
				.addPart("filename",new StringBody(name,ContentType.MULTIPART_FORM_DATA))
				.addPart("description",new StringBody(url,ContentType.MULTIPART_FORM_DATA))
				.addPart("override-existing-attachment",new StringBody("Y",ContentType.MULTIPART_FORM_DATA))
				.addBinaryBody("file", databytes , ContentType.TEXT_PLAIN, name)
				.build();
		p.setEntity(httpent);
		String result = execRequest(p, null);
		logger.debug("post run attachment result:"+result);
		return true;
	}

}
