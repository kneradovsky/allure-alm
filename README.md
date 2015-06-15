# allure-alm exporter
Export [Allure](allure.qatools.ru) results to HP ALM using alm rest api



## Usage workflow
1. Run youre tests
2. Generate [Allure](allure.qatools.ru) reports
3. Export them to the HP ALM

## Sample report
[TBD]

## Usage
One can use the the exporter either as a standalone java application or from as a maven goal. The exporter accepts up to 3 command line parameters:

1. Allure reports data folder (The folder that contains _behavior.json_ file)
2. Allute reports base url (The url of root of thepublished allure report. The root contains the _index.html_)
3. File path of the HP ALM connection properties file 

* If run with 2 parameters it loads alm-report.properties as the classpath resource 
* if run without any parameters it loads alm-report.properties as the classpath resource and assumes that system properties _dataFolder_ and _baseUrl_ point to the data folder and base url respectively

### Alm-report.properties

* _alm.resturl_ rest url of the hp alm 
* _alm.domain_ alm domain name  
* _alm.project_ name of the alm project that contains reported test cases
* _alm.user_ user that posts the report to the alm
* _alm.password_ password of the alm.user 
* _alm.baseFolder_ base folder of the reported results in the alm test lab
* _alm.resFolderPtrn_ Date Format template of the result folder 

