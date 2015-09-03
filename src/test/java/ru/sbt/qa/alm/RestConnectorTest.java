/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.sbt.qa.alm;

import com.hp.alm.rest.Entities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
//import org.junit.AfterClass;
//import org.junit.Assert;
//import org.junit.BeforeClass;
//import org.junit.Test;

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
    
    //@BeforeClass
    public static void setUpClass() {
    }
    
    //@AfterClass
    public static void tearDownClass() {
    }
    
    public Properties getAlmprops() {
        return almprops;
    }
    
    //@Test
    public void testGenerateAlmTestRuns() throws Exception {
        Map<String,String> params = new HashMap<>();
        almcon = new RestConnector("http://sbt-oaar-003/qcbin/rest/", "DEFAULT", "oat_board");
    //    Assert.assertEquals(true, almcon.login("sbt-neradovskiy-kl", "123qweasd")); 
    
        String testSetId = "301";
        Entities ents;
        List<String> testIds = new ArrayList<>();

        params.put("query","{contains-test-set.id[" + testSetId + "]}");
        ents = almcon.getEntities("/test-instances", params);
         
        if(ents.getTotalResults() != 0) {
            for (int i = 0; i < ents.getTotalResults(); i++) {
                testIds.add(AlmEntityUtils.getFieldValue(ents.getEntity().get(i), "test-id"));
            } 
        }
        
        for (String testId : testIds) {
            params.put("query","{id[" + testId + "]}");
            ents = almcon.getEntities("/tests", params);
            
            String testName = AlmEntityUtils.getFieldValue(ents.getEntity().get(0), "name");
            String encodeTestName = new String(testName.getBytes("iso-8859-1"), "UTF-8" );
            
            File feature = new File("src/test/resources/features/" + encodeTestName + ".feature");
            
            if (!feature.exists()) {
                feature.createNewFile();
                System.out.println("Фича создана");
            }
            
            String testDesciption = AlmEntityUtils.getFieldValue(ents.getEntity().get(0), "description");
            String encodeTestDesciption = new String(testDesciption.getBytes("iso-8859-1"), "UTF-8" );
            String txtUnicodetestDescription = encodeTestDesciption.replaceAll("\\&nbsp;", "").replaceAll("\\<[^>]*>", "").replaceAll("&gt;", ">")
                                                                   .replaceAll("&lt;", "<").replaceAll("&quot;", "\"").replaceAll("&laquo;", "В«").replaceAll("&raquo;", "В»");
            Writer out = new BufferedWriter(new OutputStreamWriter(
                         new FileOutputStream(feature), "UTF8"));
            try {
                out.append(txtUnicodetestDescription);
            } finally {
                out.flush();
                out.close();
            }
        }      
    }

}
