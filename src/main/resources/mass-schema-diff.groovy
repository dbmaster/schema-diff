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

modelService = dbm.getService(ModelService.class)

invService = dbm.getService(InventoryService.class)

String currentVersion = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date())

def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")

QueryRequest request = new QueryRequest(p_database_query)
request.getCriteria().add(new CustomCriterion(Operator.NEQ, "\"Deleted\"", null,null,null, true, null))

// StringBuilder emailWriter = new StringBuilder()
def xstream = new XStream();
 
def loadModel = { server_name, db_name ->
    def file_name = p_storage_folder + "/" + server_name + "_" + db_name + ".dat"
    File file = new File(file_name)
    Model model = null;
    logger.debug("Loading model from file ${file_name}")
    if(file.exists() && !file.isDirectory()) { 
        def inStream = new ObjectInputStream(new FileInputStream(file))
        try {
            model =  xstream.fromXML((String) inStream.readObject());
        } finally {
            inStream.close()
        }
    } else {
        logger.debug("File does not exist ${file_name}")
    }
    return model
}

def saveObjectToFile = { object, file_name ->
    File file = new File(file_name)
    def outStream = new ObjectOutputStream(new FileOutputStream(file))
    try {
        outStream.writeObject(xstream.toXML(object))
    } finally {
        outStream.close()
    }
}

def saveModel = { server_name, model ->
    def db_name = model.getOptions().database    
    def file_name = p_storage_folder + "/" + server_name + "_" + db_name + ".dat"
    saveObjectToFile(model, file_name)
}

for (Database db:invService.getDatabaseList(request)) {
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

/*
if (emailWriter.length() != 0) {
    EmailSender sender = dbm.getService(EmailSender.class);
    sender.createMessage(p_email, "Mass schema diff result for ${currentVersion}", "Please find database changes attached", true)
    sender.addAttachment("changes.html", emailWriter.toString())
    sender.sendMessage()
}
*/