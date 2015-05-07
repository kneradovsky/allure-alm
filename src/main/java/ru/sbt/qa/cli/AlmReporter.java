package ru.sbt.qa.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.sbt.qa.alm.AlmEntityUtils;
import ru.sbt.qa.alm.RestConnector;
import ru.yandex.qatools.allure.data.AllureBehavior;
import ru.yandex.qatools.allure.data.AllureFeature;
import ru.yandex.qatools.allure.data.AllureStory;
import ru.yandex.qatools.allure.data.AllureTestCaseInfo;
import ru.yandex.qatools.allure.data.Statistic;
import ru.yandex.qatools.allure.data.Time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Field.Value;
import com.hp.alm.rest.Fields;




class AlmTestCase {
	String testId;
	String testInstId,testCycleId;
	AllureStory story;
	AllureFeature feature;
	AlmTestCase(String tid,AllureStory st,AllureFeature feat) {
		testId=tid;
		story=st;
		feature=feat;
	}
}

public class AlmReporter {
	AllureBehavior behav;
	List<AlmTestCase> almtcs;
	protected final static String fnBehav="behavior.json";
	Properties almprops;
	RestConnector almcon;
	Logger logger = LoggerFactory.getLogger(AlmReporter.class);
	String template = "";
	
	public static void main(String[] args) {
		String baseUrl,dataFolder;
		Properties props = new Properties();
		try {
			InputStream is;
			System.out.println(Arrays.toString(args));
			if(args.length>=3 && !(args[2]==null || args[2].isEmpty()))
				is=new FileInputStream(args[2]);
			else is=AlmReporter.class.getResourceAsStream("/alm-report.properties");
			props.load(new InputStreamReader(is,RestConnector.restCharset));
		} catch(IOException e) {
			System.out.println("Alm properties load error");
			e.printStackTrace();
			return;
		}
		String template="";
		try {
			Optional<String> commentpath=Optional.ofNullable(props.getProperty("alm.commentTemplate"));
			template=IOUtils.toString(AlmReporter.class.getResourceAsStream(commentpath.orElse("/alm-comment.html")),RestConnector.restCharset);
		} catch(Throwable e) {
			System.out.println("Comments template load error");
			e.printStackTrace();
			return;
		}
		if(args.length>=2) {
			dataFolder=args[0];
			baseUrl=args[1];
		} else {
			baseUrl=System.getProperty("baseUrl", "");
			dataFolder=System.getProperty("dataFolder","");
		}
		
		AlmReporter rep = new AlmReporter();
		rep.almprops=props;
		rep.template=template;
		rep.readReportData(dataFolder);
		rep.generateAlmTestRuns(baseUrl);
		System.out.println("Done");
		System.exit(0);
	}
	
	protected void readReportData(String reportFolder) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			behav = mapper.readValue(new File(reportFolder+File.separator+fnBehav), AllureBehavior.class);
			almtcs=new ArrayList<>();
			for(AllureFeature f : behav.getFeatures()) 
				for(AllureStory st : f.getStories()) {
					String title = st.getTitle();
					String[] tparts = title.split(" ");
					try {
						Integer testId=Integer.parseInt(tparts[0]);
						almtcs.add(new AlmTestCase(testId.toString(),st,f));
					} catch(NumberFormatException e) {
						logger.debug("The title '"+title+"' doesn't start with number", e);
					}
				}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void generateAlmTestRuns(String repBaseURL) {
		try {
			almcon = new RestConnector(almprops.getProperty("alm.resturl"), almprops.getProperty("alm.domain"), almprops.getProperty("alm.project"));
			if(!almcon.login(almprops.getProperty("alm.user"), almprops.getProperty("alm.password"))) {
				logger.error("Login failed");
				return;
			}
			String currentFolder = almprops.getProperty("alm.baseFolder","/")+"/"+
					LocalDateTime.now().format(DateTimeFormatter.ofPattern(almprops.getProperty("alm.resFolderPtrn","yyyyMMddHHmmSS")));
			Entity folder=almcon.createFolder(currentFolder);
			String folderId=AlmEntityUtils.getFieldValue(folder, "id");
			String owner = System.getProperty("user.name");
			String hostname = "local";
			try {
				hostname=InetAddress.getLocalHost().getHostName();
			} catch(UnknownHostException e) {
				logger.debug("Host resolution error",e);
			}
			for(AlmTestCase tc : almtcs) {
				//check and create test set
				Entity testSet=almcon.checkAndCreateTestSet(folderId, tc.feature.getTitle());
				String testSetId=AlmEntityUtils.getFieldValue(testSet, "id");
				Entity testInstance=almcon.checkAndCreateTestInstance(testSetId, tc.testId);
				Map<String,String> fldsTestInst=AlmEntityUtils.entity2Map(testInstance);
				String testInstanceId=fldsTestInst.get("id");
				tc.testInstId=testInstanceId;
				tc.testCycleId=testSetId;
				String status,comments;
				Statistic stats = tc.story.getStatistic();
				if(stats.getFailed()>0)
					status="Failed";
				else if(stats.getPending()>0)
					status="Not Completed";
				else if(stats.getPassed()!=stats.getTotal())
					status="Blocked";
				else status="Passed";
				
				//change test instance
				LocalDateTime curDateTime = LocalDateTime.now();
				String curDate=curDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),curTime=curDateTime.format(DateTimeFormatter.ofPattern("hh:MM:ss"));
				fldsTestInst.put("status", status);
				fldsTestInst.put("exec-date", curDate);
				fldsTestInst.put("exec-time", curTime);
				fldsTestInst.put("last-modified", curDate +" "+curTime);
				fldsTestInst.put("actual-tester", owner);
				testInstance = AlmEntityUtils.map2Entity("test-instance", fldsTestInst);
				Entity resent=almcon.putEntity("/test-instances/"+tc.testInstId, testInstance);				
				//get the test run id that has been just created
				Map<String,String> params = new HashMap<>();
				//get the number of test instances in the testcase
				params.put("query","{cycle-id["+testSetId+"]}");
				params.put("order-by","{id[DESC]}");
				Entities ents = almcon.getEntities("/runs", params);
				boolean createTestRun=true;
				String testRunId="";
				if(ents.getTotalResults()!=0) {
					createTestRun=false;
					testRunId=AlmEntityUtils.getFieldValue(ents.getEntity().get(0),"id");
				} 
					
				
				String runname=tc.story.getTitle().substring(tc.testId.length()+1); //return Title without testid
				comments = statisticToString(tc.story.getStatistic()).replaceAll("\\$\\{tcname\\}", runname);  
				
				
				Long duration = tc.story.getTestCases().stream().map(AllureTestCaseInfo::getTime).collect(Collectors.summingLong(Time::getDuration));
				Long durationInSec;durationInSec = (durationInSec=duration/1000)>0 ? durationInSec : 1;
				Entity postent = new Entity().withType("run")
						.withFields(new Fields().withField(
								new Field().withName("cycle-id").withValue(new Value().withValue(tc.testCycleId)),
								new Field().withName("name").withValue(new Value().withValue(runname)),
								new Field().withName("status").withValue(new Value().withValue(status)),
								new Field().withName("test-id").withValue(new Value().withValue(tc.testId)),
								new Field().withName("testcycl-id").withValue(new Value().withValue(tc.testInstId)),
								new Field().withName("owner").withValue(new Value().withValue(owner)),
								new Field().withName("comments").withValue(new Value().withValue(comments)),
								new Field().withName("subtype-id").withValue(new Value().withValue("hp.qc.run.MANUAL")),
								new Field().withName("duration").withValue(new Value().withValue(durationInSec.toString())),
								new Field().withName("host").withValue(new Value().withValue(hostname))
								));
				if(createTestRun)
					resent = almcon.postEntity("/runs/", postent);
				else 
					resent= almcon.putEntity("/runs/"+testRunId, postent);
				//add attachment
				almcon.postRunUrlAttachment(resent,runname+"result.url",repBaseURL+"#/features/"+tc.story.getUid());
			}
		} catch(JAXBException e) {
			logger.debug("marshal/unmarshal error",e);
		}
	}
	
	protected String statisticToString(Statistic stat) {
		
		
		String res = template.replaceAll("\\$\\{passed\\}", new Long(stat.getPassed()).toString()).
				replaceAll("\\$\\{failed\\}", new Long(stat.getFailed()).toString()).
				replaceAll("\\$\\{pending\\}", new Long(stat.getPending()).toString()).
				replaceAll("\\$\\{broken\\}", new Long(stat.getBroken()).toString()).
				replaceAll("\\$\\{canceled\\}", new Long(stat.getCanceled()).toString()).
				replaceAll("\\$\\{total\\}", new Long(stat.getTotal()).toString());
		return res;
	}
}
