package ru.sbt.qa.alm;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.hp.alm.rest.Fields;
import com.hp.alm.rest.ObjectFactory;


/**
 * Utility class for ALM rest API
 */
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
	public final static String restCharset="UTF-8";

	/**
	 * Constructs rest connector object
	 * @param url - url of the ALM rest api endpoint
	 * @param domain - name of the ALM domain to connect to
	 * @param project - name of the ALM project to connect to
	 * @throws JAXBException
	 */
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

	/**
	 * Perform login to the ALM project
	 * @param username
	 * @param password
	 * @return true if successful, false otherwise
	 */
	public boolean login(String username,String password) {
		String unamepass=username+":"+password;
		String authstr = Base64.getEncoder().encodeToString(unamepass.getBytes());
		HttpGet authget = new HttpGet(resturl+"/is-authenticated");
		HttpPost sespost = new HttpPost(baseurl+"/site-session");
		authget.setConfig(rqcnf);
		try {
			HttpResponse resp = client.execute(authget);
			//check if already authenticated
			if(resp.getStatusLine().getStatusCode()==HttpURLConnection.HTTP_OK) {
				logger.debug("Already authenticated");
				return true;
			}
			authget.releaseConnection();
			//start authentication process. ALM returns authentication URL in the WWW-Authenticate header
			String wwwauth = resp.getFirstHeader("WWW-Authenticate").getValue();
			if(wwwauth!=null) {
				String[] authparts = wwwauth.split("=\"");
				wwwauth = authparts[1].substring(0,authparts[1].length()-1);
			} else {
				logger.debug("noe www-authenticate header, using rest url as auth point");
				wwwauth=resturl;
			}
			//send auth request
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
			//we need to request /site-session to store QCSession on the ALM server
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

	/**
	 * Performs get request of the <i>enturl</i> with <i>params</i> parameters, reads response and returns it
	 * @param enturl
	 * @param params
	 * @return String of the complete response body or null in case of an error
	 */
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
			HttpEntity ent = resp.getEntity();
			String entContents = EntityUtils.toString(ent);
			if(code==HttpURLConnection.HTTP_OK) {
				return entContents;
			} else {
				logger.debug("ServerError: "+resp.getStatusLine().toString());
				logger.debug("Body:"+entContents);
			}
			return null;
		} catch (URISyntaxException | IOException e) {
			logger.debug("execRequest exception "+url, e);
			return null;
		} finally {
			if(req!=null) req.releaseConnection();
		}
		
		
	}

	/**
	 * Performs {@link org.apache.http.client.methods.HttpEntityEnclosingRequestBase} (POST,PUT) with <i>data</i> body and returns response or null in case of error
	 * @param req
	 * @param data
	 * @return String of the complete response body or null in case of an error
	 */
	public String execRequest(HttpEntityEnclosingRequestBase req,String data) {
		try {
			if(data!=null) {
				req.setEntity(new StringEntity(data,restCharset));
				req.setHeader("Content-type", "application/xml");
			}
			HttpResponse resp = client.execute((HttpUriRequest) req);
			logger.debug("request:"+req.getRequestLine().toString());
			logger.debug(resp.getStatusLine().toString());
			logger.debug(Arrays.toString(resp.getAllHeaders()));
			int code = resp.getStatusLine().getStatusCode();
			HttpEntity ent = resp.getEntity();
			String entContents = EntityUtils.toString(ent,restCharset);
			if(code>=200 && code<=399) {
				return entContents;				
			} else {
				//report 
				logger.info("Server error:"+req.getRequestLine().toString());
				logger.info(entContents);
			}
		} catch (IOException e) {
			logger.debug("execRequest exception "+req.getRequestLine().toString(), e);
		} finally {
			req.releaseConnection();
		}
		return null;
	}

	/**
	 * JAXB unmarshalling <i>content</i> to the class <i>T</i>
	 * @param content
	 * @param cls
	 * @param <T>
	 * @return
	 */
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

	/**
	 * JAXB marshalling object of type T to the String
	 * @param obj
	 * @param <T>
	 * @return
	 */
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

	/**
	 * Performs get request of the <i>enturl</i> with <i>params</i> parameters, reads @{link Entity} response and returns it
	 * @param url
	 * @param params
	 * @return {@link Entity} or null in case of error
	 */
	public Entity getEntity(String url,Map<String,String> params) {
		String content = get(url,params);
		return unmarshall(content, Entity.class);
	}

	/**
	 * Performs get request of the <i>enturl</i> with <i>params</i> parameters, reads @{link Entities} response and returns it
	 * @param url
	 * @param params
	 * @return {@link Entities} or null in case of error
	 */
	public Entities getEntities(String url,Map<String,String> params) {
		String content = get(url,params);
		return unmarshall(content, Entities.class);
	}
	/**
	 * Performs POST {@link Entity} ent request to the url, reads {@link Entity} response and returns it
	 * @param url
	 * @param ent - {@link Entity} to post
	 * @return String of the complete response body or null in case of an error
	 */
	public Entity postEntity(String url,Entity ent) {
		String posturi=resturl+url;
		HttpPost p = new HttpPost(posturi);
		return storeEntity(p, ent);
	}
	/**
	 * Performs PUT {@link Entity} ent request to the url, reads {@link Entity} response and returns it
	 * @param url
	 * @param ent - {@link Entity} to put
	 * @return String of the complete response body or null in case of an error
	 */
	public Entity putEntity(String url,Entity ent) {
		String posturi=resturl+url;
		HttpPut p = new HttpPut(posturi);
		return storeEntity(p, ent);
	}

	/**
	 * Performs {@link org.apache.http.client.methods.HttpEntityEnclosingRequestBase} (POST,PUT) {@link Entity} ent request to the url, reads {@link Entity} response and returns it
	 * @param req
	 * @param ent - {@link Entity} to put
	 * @return String of the complete response body or null in case of an error
	 */
	public Entity storeEntity(HttpEntityEnclosingRequestBase req, Entity ent) {
		ObjectFactory fact = new ObjectFactory();
		String content = marshall(fact.createEntity(ent));
		if(content==null) return null;
		String result=execRequest(req, content);
		return unmarshall(result, Entity.class);
	}

	/**
	 * Adds <i>url</i> attachment named <i>name</i> to the {@link Entity} ent and post the ent to the ALM server
	 * @param ent - {@link Entity} to modify
	 * @param name - name of the attachment
	 * @param url - body of the attachment (URL)
	 * @return
	 */
	public boolean postRunUrlAttachment(Entity ent,String name,String url) {
		String runid = AlmEntityUtils.getFieldValue(ent, "id");
		String posturi=resturl+"/runs/"+runid+"/attachments";
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

	/**
	 * Recursively creates folder <i>folderName</i> and all its parents if they don't exist
	 * @param folderName
	 * @return {@link Entity} of the created folder
	 */
	public Entity createFolder(String folderName) {
		Path path = Paths.get(folderName);
		String currentParentId="0";
		Entity currentFolder=null;
		for(int i=0;i<path.getNameCount();i++) {
			String name=path.getName(i).toString();
			try {
				currentFolder=checkAndCreateFolder(currentParentId, name);
				currentParentId=AlmEntityUtils.getFieldValue(currentFolder, "id");
			} catch(NullPointerException e) {
				logger.error("NullPointer while creating test folders",e);
				return null;
			}
		}
		return currentFolder;
	}

	/**
	 * Checks if folder <i>name</i> exists as a child of the folder with id = <i>parentId</i> and creates it if it doesn't exist
	 * @param parentId
	 * @param name
	 * @return {@link Entity} of the created folder
	 */
	protected Entity checkAndCreateFolder(String parentId,String name) {
		Map<String,String> params = new HashMap<>();
		params.put("query","{parent-id["+parentId+"];name['"+name+"']}");
		Entities ents = getEntities("/test-set-folders", params);
		if(ents.getTotalResults()!=0) {
			return ents.getEntity().get(0);
		}
		//create folder;
		Entity ent = new Entity().withType("test-set-folder").withFields(new Fields().withField(
				new Field().withName("name").withValue(new Value().withValue(name)),
				new Field().withName("parent-id").withValue(new Value().withValue(parentId))
		));
		Entity resent = postEntity("/test-set-folders", ent);
		return resent;
	}

	/**
	 * Checks if testcase <i>tsName</i> exists as a child of the folder with id = <i>parentId</i> and creates it if it doesn't exist
	 * @param parentId
	 * @param tsName
	 * @return {@link Entity} of the created test case
	 */
	public Entity checkAndCreateTestSet(String parentId,String tsName) {
		Map<String,String> params = new HashMap<>();
		params.put("query","{parent-id["+parentId+"];name['"+tsName+"']}");
		Entities ents = getEntities("/test-sets", params);
		if(ents.getTotalResults()!=0) {
			return ents.getEntity().get(0);
		}
		//create test set;
		Entity ent = new Entity().withType("test-set").withFields(new Fields().withField(
				new Field().withName("name").withValue(new Value().withValue(tsName)),
				new Field().withName("parent-id").withValue(new Value().withValue(parentId)),
				new Field().withName("subtype-id").withValue(new Value().withValue("hp.qc.test-set.default"))
		));
		Entity resent = postEntity("/test-sets", ent);
		return resent;		
	}
	/**
	 * Checks if test instance of the <i>testId</i> exists in the test set <i>testSetId</i> and creates it if it doesn't exist
	 * @param testSetId
	 * @param testId
	 * @return {@link Entity} of the created test instanse
	 */
	public Entity checkAndCreateTestInstance(String testSetId,String testId) {
		Map<String,String> params = new HashMap<>();
		//get the number of test instances in the testcase
		params.put("query","{cycle-id["+testSetId+"]}");
		Entities ents = getEntities("/test-instances", params);
		Integer testOrder = ents.getTotalResults()+1;
		params.put("query","{cycle-id["+testSetId+"];test-id['"+testId+"']}");
		ents = getEntities("/test-instances", params);
		if(ents.getTotalResults()!=0) {
			return ents.getEntity().get(0);
		}
		//create test set;
		Entity ent = new Entity().withType("test-instance").withFields(new Fields().withField(
				new Field().withName("cycle-id").withValue(new Value().withValue(testSetId)),
				new Field().withName("test-id").withValue(new Value().withValue(testId)),
				//new Field().withName("test-order").withValue(new Value().withValue(testOrder.toString())),
				new Field().withName("subtype-id").withValue(new Value().withValue("hp.qc.test-instance.MANUAL"))
		));
		Entity resent = postEntity("/test-instances", ent);
		return resent;		
		
	}

}
