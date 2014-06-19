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

import com.thoughtworks.xstream.XStream

import java.io.*
import java.text.SimpleDateFormat

import com.branegy.dbmaster.sync.api.SyncService

modelService = dbm.getService(ModelService.class)
syncService = dbm.getService(SyncService.class)
invService = dbm.getService(InventoryService.class)

String currentVersion = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date())
def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")

def xstream = new XStream();

def saveObjectToFile = { object, file_name ->
    File file = new File(file_name)
    file.getParentFile().mkdirs();
    def outStream = new FileOutputStream(file)
    try {
        xstream.toXML(object,outStream)
    } finally {
        outStream.close()
    }
}

 
def loadModel = { server_name, db_name ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/model.dat"
    File file = new File(file_name)
    file.getParentFile().mkdirs();
    Model model = null;
    logger.debug("Loading model from file ${file_name}")
    if(file.exists() && !file.isDirectory()) { 
         model =  xstream.fromXML(file);
    } else {
        logger.debug("File does not exist ${file_name}")
    }
    return model
}

def saveModel = { server_name, model ->
    def db_name = model.getOptions().database
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/model.dat"
    saveObjectToFile(model, file_name)
}

def saveDiff = { server_name, db_name, syncSession, date ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/diff-"+
        new SimpleDateFormat("yyyyMMdd_HHmmss").format(date)+ ".dat"
    saveObjectToFile(syncSession, file_name)
}

def saveErrorMessage = { server_name, db_name,  message ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/lastError.txt";
    if (message != null){
        saveObjectToFile(message, file_name)
    } else {
        new File(file_name).delete();
    }
}




for (Database db:invService.getDatabaseList(new QueryRequest(p_database_query))) {
    if (db.getDatabaseName().equalsIgnoreCase("tempdb")) {
        continue;
    }
    try{
        Date version = new Date();
        
        RevEngineeringOptions options = new RevEngineeringOptions()
        options.database = db.getDatabaseName()
                
        logger.debug("Time is ${sdf.format(version)} (fetching new model)")
        
        def targetModel = modelService.fetchModel(db.getServerName(), options);
        def sourceModel = loadModel(db.getServerName(), db.getDatabaseName());
        if (sourceModel == null){
            saveModel(db.getServerName(), targetModel);
        } else {
            SyncSession syncSession = modelService.compareModel(sourceModel, targetModel);
            if (syncSession.getSyncResult().getChangeType() != com.branegy.dbmaster.sync.api.SyncPair.ChangeType.EQUALS){
                saveModel(db.getServerName(), targetModel);
                
                String diff = syncService.generateSyncSessionPreviewHtml(syncSession, true);
                saveDiff(db.getServerName(), db.getDatabaseName(), diff, version);
            } else {
                def file_name = p_storage_folder + "/" + db.getServerName() + "/" + db.getDatabaseName() + "/model.dat"
                new File(file_name).setLastModified(version.getTime())
            }
        }
        saveErrorMessage(db.getServerName(), db.getDatabaseName(), null);
    } catch (Exception e){
        saveErrorMessage(db.getServerName(), db.getDatabaseName(), e.toString());
    
        logger.error("${db.getServerName()}.${db.getDatabaseName()}: Error occurs", e)
        println "<div>${db.getServerName()}.${db.getDatabaseName()} error: ${e.getMessage()}</div>"
    } 
}


/*

for (Database db:invService.getDatabaseList(new QueryRequest(p_database_query))) {
    if (db.getDatabaseName().equalsIgnoreCase("tempdb")) {
        continue;
    }
    def modelName = db.getServerName()+"_"+db.getDatabaseName()
    try {
        logger.debug("Time is ${sdf.format(new Date())} (loading storedModel)")
        def model = loadModel(db.getServerName(), db.getDatabaseName())

        if (model == null) {
            RevEngineeringOptions options = new RevEngineeringOptions()
            options.database = db.getDatabaseName()
        
            logger.debug("Time is ${sdf.format(new Date())} (fetching new model)")
            def newModel = modelService.fetchModel(db.getServerName(), options)
            
            println "<div>New database: ${db.getServerName()}.${db.getDatabaseName()}</div>"
        
            newModel.setName(modelName)
            newModel.setVersion(currentVersion)

            logger.debug("Time is ${sdf.format(new Date())} (saving model)")
            saveModel(db.getServerName(), newModel)
            logger.info("${db.getServerName()}.${db.getDatabaseName()}: new database")
            // emailWriter.append("<div>New database: ${db.getDatabaseName()}</div>")
        } else {
            logger.debug("Time is ${sdf.format(new Date())} (fetching model)")
            def targetModel = modelService.fetchModel(model.getConnectionName(), model.getOptions())
            logger.debug("Time is ${sdf.format(new Date())} (comparing model)")
            def syncSession = modelService.compareModel(model, targetModel)
            syncSession.setParameter("longText", "Procedure.Source;View.Source;Function.Source")

            if (syncSession.getSyncResult().getChangeType() != ChangeType.EQUALS){
                logger.debug("Time is ${sdf.format(new Date())} (generate sync)")
                def preview = invService.generateDBSyncPreview(syncSession)
                println "<div>Changes: ${db.getServerName()}.${db.getDatabaseName()} since ${model.getVersion()}</div>"
                println "<div>${preview}</div>"
            
               // emailWriter.append("""<div>Changes detected in ${db.getServerName()}.${db.getDatabaseName()} 
                //                      (${currentVersion} vs ${model.getVersion()} )</div>
                 //                     <div>${preview}</div>""");
            
                targetModel.setVersion(currentVersion)
                targetModel.setName(modelName)
                
                //def file_name = "${p_storage_folder}/${db.getServerName()}_${db.getDatabaseName()}_diff_${currentVersion}.dat"
                // saveObjectToFile(syncSession, file_name)
                // http://stackoverflow.com/questions/603013/dumping-a-java-objects-properties
                // XStream xstream = new XStream(new Sun14ReflectionProvider(new FieldDictionary(new ImmutableFieldKeySorter())), new DomDriver("utf-8"));
                
                def file_name = "${p_storage_folder}/${db.getServerName()}_${db.getDatabaseName()}_diff_${currentVersion}.html"
                new File(file_name).write(preview)

                // saveModel(db.getServerName(), targetModel)
                logger.info("${db.getServerName()}.${db.getDatabaseName()}: data structure changes found")
            } else {
                println "<div>${db.getServerName()}.${db.getDatabaseName()}: No data structure changes since ${model.getVersion()} </div>"
            
                // model.setLastSynch(targetModel.getLastSynch())
                // saveModel(db.getServerName(), model)
                logger.info("${db.getServerName()}.${db.getDatabaseName()}: No changes");
            }
        }
    } catch (Exception e) {
        logger.error("${db.getServerName()}.${db.getDatabaseName()}: Error occurs", e)
        println "<div>${db.getServerName()}.${db.getDatabaseName()} error: ${e.getMessage()}</div>"
        // emailWriter.append("<div>${db.getServerName()}.${db.getDatabaseName()} error: ${e.getMessage()}</div>");
    }
}


if (emailWriter.length() != 0) {
    EmailSender sender = dbm.getService(EmailSender.class);
    sender.createMessage(p_email, "Mass schema diff result for ${currentVersion}", "Please find database changes attached", true)
    sender.addAttachment("changes.html", emailWriter.toString())
    sender.sendMessage()
}
*/