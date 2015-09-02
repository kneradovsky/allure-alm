package ru.sbt.qa.alm;

import com.hp.alm.rest.Entities;
import java.io.BufferedWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;


public class TestSuiteFormation {
    Properties almprops;
    RestConnector almcon;
    Logger logger = LoggerFactory.getLogger(TestSuiteFormation.class);
    Entities ents;
    Map<String,String> params = new HashMap<>();
    
    
    public TestSuiteFormation() { 
    }
    
    public TestSuiteFormation setAlmprops(Properties almprops) {
        this.almprops = almprops;
        return this;
    }
    
    public Properties getAlmprops() {
        return almprops;
    }
    
    /**
     * Возвращает набор тест-кейсов для заданного тест-сьюита
     * @param suiteId
     * @throws Exception 
     */
    public void createFeaturesSuite(String suiteId) throws Exception {
        List<String> testIds = new ArrayList<>();
        String testSetId = suiteId;
        
        almcon = new RestConnector(almprops.getProperty("alm.resturl"), almprops.getProperty("alm.domain"), almprops.getProperty("alm.project"));
        
        // авторизуеся в ALM
        if(!almcon.login(almprops.getProperty("alm.user"), almprops.getProperty("alm.password"))) {
            logger.error("Login failed");
            throw new Exception("Ахтунг! Ошбика авторизации...\n");
        }
        
        // пробуем найти тест-сьюит по его id
        try {
            params.put("query","{contains-test-set.id[" + testSetId + "]}");
            ents = almcon.getEntities("/test-instances", params);
         
            if(ents.getTotalResults() != 0) {
                for (int i = 0; i < ents.getTotalResults(); i++) {
                    testIds.add(AlmEntityUtils.getFieldValue(ents.getEntity().get(i), "test-id"));
                } 
            }
        }   catch (Exception ex) {
                throw new Exception("Ахтунг! Ошибка при поиске тест-кейсов в тест-сьюите...\n", ex);
        }
        generateFeatures(testIds);
    }
    
    /**
     * Для набора тест-кейсов из ALM создает набор фич, используя поля name и description 
     * @param testids
     * @throws Exception 
     */
    protected void generateFeatures(List<String> testids) throws Exception {
        List<String> testIds = testids;
        String testName = "";
        String testDesciption = "";
        
        for (String testId : testIds) {
            // находим имя и описание каждого тест-кейса (для формирования фичи)
            try {
                params.put("query","{id[" + testId + "]}");
                ents = almcon.getEntities("/tests", params);
            
                testName = AlmEntityUtils.getFieldValue(ents.getEntity().get(0), "name");
                testDesciption = AlmEntityUtils.getFieldValue(ents.getEntity().get(0), "description");
            }   catch (Exception ex) {
                    throw new Exception("Ахтунг! Ошибка при поиске имени и описания тест-кейса...\n", ex);
            }  
            
            // перекодируем результат в UTF-8
            String encodeTestName = new String(testName.getBytes("iso-8859-1"), "UTF-8" );
            String encodeTestDesciption = new String(testDesciption.getBytes("iso-8859-1"), "UTF-8" );
            
            //создаем файл фичи с названием тест-кейса и расширением .feature
            File feature = new File("src/test/resources/features/" + encodeTestName + ".feature");
            
            if (!feature.exists()) {
                feature.createNewFile();
            }
            
            // убираем из описания тест-кейса html-тэги (или заменяем на нужные символы)
            String txtUnicodetestDescription = HtmlToTxt(encodeTestDesciption);
            
            // пишем в файл фичи описание из тест-кейса
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

    /**
     * Удаляет html-тэги и заменяем символные коды html соответствующими значениями 
     * @param text
     * @return
     * @throws Exception 
     */
    protected String HtmlToTxt(String text) throws Exception {
        String res = "";
        
        try {
            res = text.replaceAll("\\&nbsp;", "").replaceAll("\\<[^>]*>", "").replaceAll("&gt;", ">")
                      .replaceAll("&lt;", "<").replaceAll("&quot;", "\"").replaceAll("&laquo;", "«").replaceAll("&raquo;", "»");
        }   catch (Exception ex) {
                throw new Exception("Ахтунг! Ошибка при замене html-тэгов...\n", ex);
        }  
        return res;
    }

}
