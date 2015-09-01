/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.sbt.qa.alm;

import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author sbt-frantsuzov-sv
 */
public class RestConnectorTest {
    
    RestConnector almcon;
    Properties almprops;
    List<AlmTestCase> almtcs;
    
    public RestConnectorTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }

    
    //@Test
    public void testLogin() throws Exception {
        almcon = new RestConnector("http://sbt-oaar-003/qcbin/rest/", "DEFAULT", "oat_board");
        Assert.assertEquals(true, almcon.login("sbt-neradovskiy-kl", "123qweasd"));
    } 
    
    public Properties getAlmprops() {
        return almprops;
    }

    public RestConnectorTest setAlmprops(Properties almprops) {
        this.almprops = almprops;
        return this;
    }
    
    @Test
    public void testGenerateAlmTestRuns() throws Exception {
        Map<String,String> params = new HashMap<>();
        String testSetId = "";
        almcon = new RestConnector("http://sbt-oaar-003/qcbin/rest/", "DEFAULT", "oat_board");
        Assert.assertEquals(true, almcon.login("sbt-neradovskiy-kl", "123qweasd")); 
    
        String currentFolder = "/Для эксперементов с СУДИР/НКП/Релиз 2/Регресс/АС АПККБ";
        Entity folder = almcon.createFolder(currentFolder);
        String folderId = AlmEntityUtils.getFieldValue(folder, "id");
        
        Entity testSet = almcon.checkAndCreateTestSet(folderId, "Синхронизация данных");
            
        params.put("query","{parent-id[" + folderId + "];name['Синхронизация данных']}");
        Entities ents = almcon.getEntities("/test-sets", params);	
                
        if(ents.getTotalResults() != 0) {
            testSetId = AlmEntityUtils.getFieldValue(ents.getEntity().get(0), "id");
    
            params.put("query","{contains-test-set.id[" + testSetId + "]}");
            Entities ents2 = almcon.getEntities("/test-instances", params);
        
            String testId = AlmEntityUtils.getFieldValue(ents2.getEntity().get(0), "test-id");
            
            params.put("query","{id[" + testId + "]}");
            Entities ents3 = almcon.getEntities("/tests", params);
            
            String testDescription = AlmEntityUtils.getFieldValue(ents3.getEntity().get(0), "description");
        //    System.out.println(AlmEntityUtils.entity2Map(ents3.getEntity().get(0))); 
        //    System.out.println(testDescription.replaceAll("<[^.]+>","")); 
        }
    }
}
