/*
 *  File Version:  $Id: sync-test.groovy 145 2013-05-22 18:10:44Z schristin $
 */

import groovy.io.FileType

import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*

def runSQL = { fileName , targetDB ->
    def command = "osql -S localhost -b -E -d ${targetDB} -i "+fileName
    def proc = command.execute()
    proc.waitFor()
    if (proc.exitValue() > 0 ) {
        println "return code: ${ proc.exitValue()}"
        println "stderr: ${proc.err.text}"
        println "stdout: ${proc.in.text}"
    }
}


def getCreateDBQuery = { dbName -> 
    """CREATE DATABASE [${dbName}]
 ON 
 PRIMARY 
( NAME = N'data_file', FILENAME = N'C:\\temp\\databases\\${dbName}.mdf' , 
   SIZE = 4096KB , MAXSIZE = UNLIMITED, FILEGROWTH = 1024KB )
 LOG ON 
( NAME = N'log_file',  FILENAME = N'C:\\temp\\databases\\${dbName}.ldf' , 
  SIZE = 1024KB , MAXSIZE = 2048GB , FILEGROWTH = 10%)"""
}


def dir = new File("plugins/dbmaster-sync-test/single")

invService = dbm.getService(InventoryService.class)
syncService = dbm.getService(SyncService.class)

//def em = dbm.injector.getInstance(javax.persistence.EntityManager.class)
//.getTransaction().setRollbackOnly()
//println "Transaction 1 =" + em.getTransaction().isActive()
//try {
modelService = dbm.getService(ModelService)

    dir.eachFileRecurse (FileType.FILES) { file ->
        logger.info("Processing ${file.name}")
        println "------------------------- Processing test case for file: " + file.name
        println "<pre>"+file.text+"</pre>"
        println "------------------------- Sync table --------------------------------"
        
        def createDBFile = new File("c:\\temp\\databases\\00_create_db.sql")
        file.write(getCreateDBQuery(file.name))
        
        returns


        /*
        
        logger.info("Cleanup database with  000_setup.sql")
        runSQL("plugins\\dbmaster-sync-test\\000_setup.sql")

        RevEngineeringOptions options = new RevEngineeringOptions();
        options.database = "SYNC_TEST"
        def model = modelService.fetchModel("InventoryTest.localhost2", options)
        model.name = file.name
        model.version = "1"
        modelService.createModel(model, "connection.project")
        
        */
        
        println "3rd part"  
  //      em.getTransaction().commit()
        //println em.getTransaction()

        //Model model = findModelById(modelId);
        def model = modelService.findModelByName(file.name, "1", null)
        def targetModel = modelService.fetchModel(model.getConnectionName(), model.getOptions());
        def syncSession = modelService.compareModel(model, targetModel);

        logger.info("Processing "+file.name)
        
        //modelService.deleteModelObject(227L)
        // runSQL("plugins\\dbmaster-sync-test\\tests\\"+file.name)
   	
        // def syncSession = modelService.synchronizeModel(model.id)
        def preview = invService.generateDBSyncPreview(syncSession)
        println "<div>" + preview + "</div>"

        //em.getTransaction().commit()
        //em.getTransaction().begin()

        //println em.getTransaction()
        syncSession.applyChanges(modelService)
        //syncService.completeSyncSession(syncSession)
    }
//} catch (Exception e) { 
//     e.printStackTrace()
//}