package ru.sbt.qa.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.sbt.qa.alm.RestConnector;
import ru.yandex.qatools.allure.data.AllureBehavior;
import ru.yandex.qatools.allure.data.AllureFeature;
import ru.yandex.qatools.allure.data.AllureStory;
import ru.yandex.qatools.allure.data.Statistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Field.Value;
import com.hp.alm.rest.Fields;
import com.hp.alm.rest.ObjectFactory;


class AlmTestCase {
	String testId;
	String testInstId,testCycleId;
	AllureStory story;
	AlmTestCase(String tid,AllureStory st) {
		testId=tid;
		story=st;
	}
}

public class AlmReporter {
	AllureBehavior behav;
	List<AlmTestCase> almtcs;
	protected final static String fnBehav="behavior.json";
	Properties almprops;
	RestConnector almcon;
	Logger logger = LoggerFactory.getLogger(AlmReporter.class);
	
	public static void main(String[] args) {
		String baseUrl,dataFolder;
		Properties props = new Properties();
		try {
			props.load(AlmReporter.class.getResourceAsStream("/alm-report.properties"));
		} catch(IOException e) {
			e.printStackTrace();
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
		rep.readReportData(dataFolder);
		rep.generateAlmTestRuns(baseUrl);
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
						almtcs.add(new AlmTestCase(testId.toString(),st));
					} catch(NumberFormatException e) {
						logger.debug("The title '"+title+"' doesn't start with number", e);
					}
				}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected Entity createEntity(AlmTestCase tc) {
		ObjectFactory fact = new ObjectFactory();
		return null;
	}
	
	protected void generateAlmTestRuns(String repBaseURL) {
		try {
			almcon = new RestConnector(almprops.getProperty("alm.resturl"), almprops.getProperty("alm.domain"), almprops.getProperty("alm.project"));
			boolean res = almcon.login(almprops.getProperty("alm.user"), almprops.getProperty("alm.password"));
			for(AlmTestCase tc : almtcs) {
				Map<String,String> params=new HashMap<>();
				params.put("query","{test-id["+tc.testId+"]}");
				params.put("order-by", "{id[DESC]}");
				Entities ents=almcon.getEntities("/test-instances",params);
				if(ents==null) {
					logger.warn("Connection error");
					continue;
				}
				if(ents.getTotalResults()==0) {
					logger.warn("Testcase "+tc.testId+" has no test instance in the ALM. Please check if it's a part of any TestSet");
					continue;
				}
				Entity ent = ents.getEntity().get(0);
				Map<String,String> entvals = almcon.entity2Map(ent);
				tc.testInstId=entvals.get("id");
				tc.testCycleId=entvals.get("cycle-id");
				String status,owner,comments;
				Statistic stats = tc.story.getStatistic();
				if(stats.getFailed()>0)
					status="Failed";
				else if(stats.getPassed()!=stats.getTotal())
					status="Blocked";
				else status="Passed";
				owner = System.getProperty("user.name");
				comments = "<html><body> <a href=\""+repBaseURL+"#/features/"+tc.story.getUid()+"\" target=\"_blank\">Report</a></body></html>";
				String runname=tc.story.getTitle().substring(tc.testId.length()+1); //return Title without testid
				Entity postent = new Entity().withType("run")
						.withFields(new Fields().withField(
								new Field().withName("cycle-id").withValue(new Value().withValue(tc.testCycleId)),
								new Field().withName("name").withValue(new Value().withValue(runname)),
								new Field().withName("status").withValue(new Value().withValue(status)),
								new Field().withName("test-id").withValue(new Value().withValue(tc.testId)),
								new Field().withName("testcycl-id").withValue(new Value().withValue(tc.testInstId)),
								new Field().withName("owner").withValue(new Value().withValue(owner)),
								new Field().withName("comments").withValue(new Value().withValue(comments)),
								new Field().withName("subtype-id").withValue(new Value().withValue("hp.qc.run.MANUAL"))
								));
				Entity resent = almcon.postEntity("/runs/", postent);
				almcon.postRunUrlAttachment(resent,runname+"result.url",repBaseURL+"#/features/"+tc.story.getUid());
			}
		} catch(JAXBException e) {
			logger.debug("marshal/unmarshal error",e);
		}
	}
}
