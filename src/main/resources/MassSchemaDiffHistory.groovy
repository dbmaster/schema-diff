import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.inventory.model.Database
import com.branegy.service.core.exception.EntityNotFoundApiException
import com.branegy.service.core.search.CustomCriterion
import com.branegy.service.core.search.CustomCriterion.Operator
import com.branegy.dbmaster.model.RevEngineeringOptions
import com.branegy.service.core.QueryRequest
import com.branegy.dbmaster.sync.api.SyncPair.ChangeType
import com.branegy.email.EmailSender
import com.branegy.service.core.search.CustomCriterion
import java.text.ParseException;
import org.apache.commons.io.Charsets;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date

import com.branegy.dbmaster.sync.api.SyncService

import org.apache.commons.io.Charsets
import org.apache.commons.io.FilenameUtils
import com.branegy.scripting.DbMaster
import java.text.DateFormat
import java.util.Locale


public class MassSchemaDiffHistory {
    final Logger logger;
    final Date versionA;
    final Date versionB;
    final InventoryService invService = dbm.getService(InventoryService.class)
    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
    def userDf = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.US)
    
    public MassSchemaDiffHistory(Logger logger, Date versionFrom, Date versionTo, DbMaster dbm) {
        this.logger = logger;
        this.versionA = versionFrom == null ? new Date(0) : versionFrom;
        this.versionB = versionTo;
        this.invService = dbm.getService(InventoryService.class);
		logger.info("Loading history between ${versionFrom} and ${versionTo}")
    }
    
    def fileToString = { file ->
        return IOUtils.toString(file.toURI().toURL(), Charsets.UTF_8)
    }
    
    def extractVersionFromFile(file) {
        if (!file.getName().startsWith("diff-")){
            return null;
        }
        try{
            String name = FilenameUtils.removeExtension(file.getName());
            return sdf.parse(name.substring("diff-".length()));
        } catch (Exception e){
            logger.error("Can not extract file version from "+ file, e);
            return null;
        }
    }
    
    def loadErrors(file, versionA, versionB) {
        InputStream    fis;
        BufferedReader br;
        String         line;
        List<String> result = [];
        try{
            if (!file.exists() || !file.isFile()) {
                return result;
            }
            fis = new FileInputStream(file);
            br = new BufferedReader(new InputStreamReader(fis, Charsets.UTF_8));
            while ((line = br.readLine()) != null) {
                String[] split = line.split(" ",2);
                try{
                    Date version = sdf.parse(split[0]);
                    if (version.compareTo(versionA)>=0 && 
                        (versionB == null || version.compareTo(versionB)<=0)) {
                        result.add(userDf.format(version)+":"+split[1]);
                    } else if (versionB !=null && version.compareTo(versionB)>0) {
                        break;  
                    }
                } catch (ParseException e) {
                    logger.error("Illegal log date format {}", split[0]);
                }
            }
            // select top ?
        } finally {
            IOUtils.closeQuietly(br);
            IOUtils.closeQuietly(fis);
        }
        return result;
    }
    
    
    public String getHistory(String p_database_query, String p_storage_folder) {
        StringBuilder emailContent = new StringBuilder();
        StringBuilder builder = new StringBuilder();
        for (Database db:invService.getDatabaseList(new QueryRequest(p_database_query))) {
            if (db.getDatabaseName().equalsIgnoreCase("tempdb")) {
                continue;
            }
            def server_name = db.getServerName();
            def db_name =  db.getDatabaseName();
            
            try{
                def dir = new File(p_storage_folder + "/" + server_name + "/" + db_name);
                if (!dir.exists() || !dir.isDirectory() || !new File(dir,"model.dat").exists()){
                    logger.warn("Server {}, database {} exists in inventory but is not accessible at the server", 
                                 db.getServerName(), db.getDatabaseName());
                    def errorFile = new File(dir, "messages.txt");
                    if (!errorFile.exists() || !errorFile.isFile()){
                        emailContent.append("<h2>Server "+server_name+", "+db_name+" model is not found!</h2><br/>");
                        continue;
                    }
                }
                
                builder.setLength(0);
                        
                def errorContent = loadErrors(new File(dir,"messages.txt"), versionA, versionB);
                
                def diffFiles = dir.listFiles(new FileFilter(){
                    public boolean accept(File pathname){
                        Date version = extractVersionFromFile(pathname);
                        return version!=null && version.compareTo(versionA) >= 0 &&
                            (versionB == null ||  version.compareTo(versionB) <= 0);
                    }
                });
                diffFiles.sort{ a,b -> a.getName().compareTo(b.getName())};
                for (File f:diffFiles){
                    Date version = extractVersionFromFile(f);
                    builder.append("<div> "+ userDf.format(version) +"</div>!!");
                    builder.append("<div>"+ fileToString(f) +"</div>");
                }
                if (!errorContent.isEmpty()) {
                    for (String msg:errorContent) {
                        builder.append("<div>${msg}</div>");
                    }
                }
                
                if (builder.length()!=0){
                    emailContent.append("<h2>Server "+server_name+", "+db_name+"</h2>");
                    emailContent.append(builder.toString());
                    emailContent.append("<br/>");
                }
            } catch (Exception e){
                logger.error("${db.getServerName()}.${db.getDatabaseName()}: Error occurs", e)
                emailContent.append("<h2>Server ${server_name}, ${db_name}</h2>");
                emailContent.append("<div>Error: ${e.getMessage()}</div>");
            }
        }
        return emailContent.toString();
    }

}