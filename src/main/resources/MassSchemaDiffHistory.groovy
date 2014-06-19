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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date

import com.branegy.dbmaster.sync.api.SyncService

import org.apache.commons.io.Charsets
import org.apache.commons.io.FilenameUtils
import com.branegy.scripting.DbMaster

public class MassSchemaDiffHistory{
    final Logger logger;
    final Date versionA;
    final Date versionB;
    final InventoryService invService = dbm.getService(InventoryService.class)
    
    public MassSchemaDiffHistory(Logger logger, Date versionFrom, Date versionTo, DbMaster dbm){
        this.logger = logger;
        this.versionA = versionFrom == null ? new Date(0) : versionFrom;
        this.versionB = versionTo;
        this.invService = dbm.getService(InventoryService.class);
    }
    
    def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
    
    def fileToString = { file ->
        return IOUtils.toString(file.toURI().toURL(), Charsets.UTF_8)
    }
    
    def extractVersionFromFile(file){
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
    
    public String getHistory(String p_database_query, String p_storage_folder){
        StringBuilder emailContent = new StringBuilder();
        for (Database db:invService.getDatabaseList(new QueryRequest(p_database_query))) {
            if (db.getDatabaseName().equalsIgnoreCase("tempdb")) {
                continue;
            }
            def server_name = db.getServerName();
            def db_name =  db.getDatabaseName();
            
            try{
                def dir = new File(p_storage_folder + "/" + server_name + "/" + db_name);
                if (!dir.exists() || !dir.isDirectory() || !new File(dir,"model.dat").exists()){
                    logger.warn("Server {}, database {} is not exists", db.getServerName(), db.getDatabaseName());
                    def errorFile = new File(dir,"lastError.txt");
                    if (!errorFile.exists() || !errorFile.isFile()){
                        emailContent.append("<h2>Server "+server_name+", "+db_name+" model is not found!</h2><br/>");
                        continue;
                    }
                }
                
                emailContent.append("<h2>Server "+server_name+", "+db_name+"</h2><br/>");
                        
                def errorContent = null;
                def errorVersion = null;
                def errorFile = new File(dir,"lastError.txt");
                if (errorFile.exists() && errorFile.isFile()
                    && (errorVersion = new Date(errorFile.lastModified())).compareTo(versionA)>=0
                    && (versionB == null || errorVersion.compareTo(versionB) <= 0)){
                    errorContent = fileToString(errorFile);
                }
                
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
                    emailContent.append("<div>Date "+ version +"</div>");
                    emailContent.append("<div>"+ fileToString(f) +"</div>");
                }
                if (errorContent!=null){
                    emailContent.append("<div>Date "+ errorVersion +"</div>");
                    emailContent.append("<div>" + errorContent + "</div>");
                }
                emailContent.append("<br/>");
            } catch (Exception e){
                logger.error("${db.getServerName()}.${db.getDatabaseName()}: Error occurs", e)
                emailContent.append("<div>Error: ${e.getMessage()}</div>");
            }
        }
        return emailContent.toString();
    }

}