package ru.sbt.qa.alm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.qatools.allure.data.*;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by sbt-neradovskiy-kl on 20.07.2015.
 * version 1.0
 * Test case data storage class
 */
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
/**
 * Created by sbt-neradovskiy-kl on 20.07.2015.
 * version 1.0
 * ALM Reporter class
 */
public class Reporter {
    AllureBehavior behav;
    List<AlmTestCase> almtcs;
    protected final static String fnBehav="behavior.json";
    Properties almprops;
    RestConnector almcon;
    Logger logger = LoggerFactory.getLogger(Reporter.class);
    String template = "";

    /**
     * Reads behavior.json from allure report results and fills collection of the test cases that are going to be loaded to the ALM.
     * Only tests that start with a number are added to the collection
     * @param reportFolder
     * @return
     */
    public boolean readReportData(String reportFolder) {
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
            return false;
        }
        return true;
    }

    public Properties getAlmprops() {
        return almprops;
    }

    public Reporter setAlmprops(Properties almprops) {
        this.almprops = almprops;
        return this;
    }

    public String getTemplate() {
        return template;
    }

    public Reporter setTemplate(String template) {
        this.template = template;
        return this;
    }

    /**
     * Generates ALM results for the testcases loaded by readReportData method
     * @param repBaseURL - base url of the <b>published</b> allure report
     */
    public void generateAlmTestRuns(String repBaseURL) {
        try {
            almcon = new RestConnector(almprops.getProperty("alm.resturl"), almprops.getProperty("alm.domain"), almprops.getProperty("alm.project"));
            if(!almcon.login(almprops.getProperty("alm.user"), almprops.getProperty("alm.password"))) {
                logger.error("Login failed");
                return;
            }
            //construct folder name as concatenation of the constant part - alm.baseFolder and variable part base on the current time
            //variable part is formatted as specified in the alm.resFolderPtrn property
            String currentFolder = almprops.getProperty("alm.baseFolder","/")+"/"+
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(almprops.getProperty("alm.resFolderPtrn", "yyyyMMddHHmmSS")));
            Entity folder=almcon.createFolder(currentFolder);
            String folderId=AlmEntityUtils.getFieldValue(folder, "id");
            String owner = System.getProperty("user.name");
            String hostname = "local";
            try {
                hostname= InetAddress.getLocalHost().getHostName();
            } catch(UnknownHostException e) {
                logger.debug("Host resolution error",e);
            }
            logger.info("Going to publish "+almtcs.size()+" testcases");
            for(AlmTestCase tc : almtcs) {
                //check and create test set for the results
                Entity testSet=almcon.checkAndCreateTestSet(folderId, tc.feature.getTitle());
                String testSetId=AlmEntityUtils.getFieldValue(testSet, "id");
                //check and create test set for the current result
                Entity testInstance=almcon.checkAndCreateTestInstance(testSetId, tc.testId);
                Map<String,String> fldsTestInst=AlmEntityUtils.entity2Map(testInstance);
                String testInstanceId=fldsTestInst.get("id");
                tc.testInstId=testInstanceId;
                tc.testCycleId=testSetId;
                String status,comments;
                //get the stats of the current test case
                Statistic stats = tc.story.getStatistic();
                if(stats.getFailed()>0)
                    status="Failed";
                else if(stats.getPending()>0)
                    status="Not Completed";
                else if(stats.getPassed()!=stats.getTotal())
                    status="Blocked";
                else status="Passed";
                logger.info("Publishing testcase "+tc.story.getTitle()+" finished with status: " + status);
                //modify test instance. Add real data - status and dates
                LocalDateTime curDateTime = LocalDateTime.now();
                String curDate=curDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),curTime=curDateTime.format(DateTimeFormatter.ofPattern("hh:MM:ss"));
                fldsTestInst.put("status", status);
                fldsTestInst.put("exec-date", curDate);
                fldsTestInst.put("exec-time", curTime);
                fldsTestInst.put("last-modified", curDate +" "+curTime);
                fldsTestInst.put("actual-tester", owner);
                testInstance = AlmEntityUtils.map2Entity("test-instance", fldsTestInst);
                almcon.putEntity("/test-instances/"+tc.testInstId, testInstance);
                //get the test run id that has been just created
                Map<String,String> params = new HashMap<>();
                //get the number of test runs of the test instance
                params.put("query","{cycle-id["+testSetId+"]}");
                params.put("order-by","{id[DESC]}");
                Entities ents = almcon.getEntities("/runs", params);
                boolean createTestRun=true;
                String testRunId="";
                if(ents.getTotalResults()!=0) { //check if test instance already has any run. Get the id of the last one
                    createTestRun=false;
                    testRunId=AlmEntityUtils.getFieldValue(ents.getEntity().get(0),"id");
                }


                String runname=tc.story.getTitle().substring(tc.testId.length()+1); //return Title without testid
                comments = statisticToString(tc.story.getStatistic()).replaceAll("\\$\\{tcname\\}", runname);


                Long duration = tc.story.getTestCases().stream().map(AllureTestCaseInfo::getTime).collect(Collectors.summingLong(Time::getDuration));
                Long durationInSec;durationInSec = (durationInSec=duration/1000)>0 ? durationInSec : 1;
                //create and fill test run data
                Entity postent = new Entity().withType("run")
                        .withFields(new Fields().withField(
                                new Field().withName("cycle-id").withValue(new Field.Value().withValue(tc.testCycleId)),
                                new Field().withName("name").withValue(new Field.Value().withValue(runname)),
                                new Field().withName("status").withValue(new Field.Value().withValue(status)),
                                new Field().withName("test-id").withValue(new Field.Value().withValue(tc.testId)),
                                new Field().withName("testcycl-id").withValue(new Field.Value().withValue(tc.testInstId)),
                                new Field().withName("owner").withValue(new Field.Value().withValue(owner)),
                                new Field().withName("comments").withValue(new Field.Value().withValue(comments)),
                                new Field().withName("subtype-id").withValue(new Field.Value().withValue("hp.qc.run.MANUAL")),
                                new Field().withName("duration").withValue(new Field.Value().withValue(durationInSec.toString())),
                                new Field().withName("host").withValue(new Field.Value().withValue(hostname))
                        ));
                Entity resent;
                if(createTestRun)
                    resent = almcon.postEntity("/runs/", postent); //create new test run
                else
                    resent= almcon.putEntity("/runs/" + testRunId, postent); //modify test run
                //add attachment
                almcon.postRunUrlAttachment(resent,"case "+tc.testId+" result.url",repBaseURL+"#/features/"+tc.story.getUid());
            }
        } catch(JAXBException e) {
            logger.debug("marshal/unmarshal error",e);
        }
        catch (Throwable e) {
            logger.info("General error",e);
        }
    }

    /**
     * Convert statistic to the HTML string using the <i>template</i>
     * @param stat
     * @return
     */
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
